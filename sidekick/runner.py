# -*- coding:utf-8 -*-
#
# Copyright (C) 2019, Maximilian Köhl <mkoehl@cs.uni-saarland.de>

from __future__ import annotations

from typing import List, Tuple, Union

import asyncio
import os
import pathlib
import sys
import time
import warnings

from asyncio import subprocess
from dataclasses import dataclass, field

try:
    import psutil
except ImportError:
    psutil = None
    warnings.warn("Psutil unavailable! CPU affinity will not have any effect!")

from .printer import Page


if sys.platform == 'win32':
    asyncio.set_event_loop_policy(asyncio.WindowsProactorEventLoopPolicy())


@dataclass(eq=False)
class Handle:
    configuration: pathlib.Path

    server: asyncio.AbstractServer = None
    port: int = None

    connected: asyncio.Event = field(default_factory=asyncio.Event)

    reader: asyncio.StreamReader = None
    writer: asyncio.StreamWriter = None

    shutdown: asyncio.Event = field(default_factory=asyncio.Event)
    closed: asyncio.Event = field(default_factory=asyncio.Event)

    def segments(self, *segments: Union[str, bytes]):
        for segment in segments:
            self.chunk(segment)
            self.end_of_segment()

    def end_of_paragraph(self):
        self.chunk('\n\n')

    def end_of_segment(self):
        self.chunk('\n\n\n')

    def chunk(self, source: Union[str, bytes]):
        if isinstance(source, str):
            self.writer.write(source.encode('utf-8'))
        else:
            self.writer.write(source)

    def eof(self):
        self.writer.write_eof()

    async def read_page(self, timeout: float = 20):
        try:
            chunk = (await asyncio.wait_for(
                self.reader.readuntil(b'\0\n'), timeout=timeout
            ))[:-2]
            return Page.load(chunk)
        except asyncio.IncompleteReadError as error:
            if error.partial:
                return Page.load(error.partial)

    async def iter_pages(self, timeout: float = 20):
        while not self.reader.at_eof():
            yield await self.read_page(timeout)

    async def start(self) -> str:
        self.server = await asyncio.start_server(self.handle, host='127.0.0.1')
        self.port = self.server.sockets[0].getsockname()[1]
        return f'tcp://127.0.0.1:{self.port}'

    async def handle(self, reader: asyncio.StreamReader, writer: asyncio.StreamWriter):
        assert not self.connected.is_set()
        self.reader = reader
        self.writer = writer
        self.connected.set()
        await self.shutdown.wait()
        self.writer.close()
        self.server.close()
        await self.server.wait_closed()
        self.closed.set()

    async def close(self):
        self.shutdown.set()
        if self.connected.is_set():
            await self.closed.wait()

    async def drain(self, timeout: float = 200):
        return await asyncio.wait_for(self.reader.read(), timeout=timeout)


Program = List[Union[str, pathlib.Path]]


@dataclass(eq=False)
class Runner:
    program: Program

    configurations: List[pathlib.Path] = field(default_factory=list)

    capture_output: bool = False

    affinity: Tuple[int] = tuple(range(os.cpu_count()))

    process: subprocess.Process = None

    handles: List[Handle] = None

    running: asyncio.Event = field(default_factory=asyncio.Event)

    async def wait_for_handles(self, timeout: float = 5):
        await asyncio.wait_for(asyncio.gather(
            *(handle.connected.wait() for handle in self.handles)
        ), timeout=timeout)

    async def wait_for_termination(self, timeout: float = 300) -> int:
        return await asyncio.wait_for(self.process.wait(), timeout)

    async def __aenter__(self):
        assert self.process is None and self.handles is None
        cmd = self.program[:]
        self.handles = []
        for configuration in self.configurations:
            handle = Handle(configuration)
            cmd.extend((configuration, 'socket', await handle.start()))
            self.handles.append(handle)
        if self.capture_output:
            self.process = await subprocess.create_subprocess_exec(
                *map(str, cmd), stderr=subprocess.PIPE, stdout=subprocess.PIPE
            )
        else:
            self.process = await subprocess.create_subprocess_exec(
                *map(str, cmd)
            )
        if psutil is not None:
            psutil.Process(self.process.pid).cpu_affinity(self.affinity)
        self.running.set()
        return self

    async def __aexit__(self, exc_type, exc_value, exc_tb):
        for handle in self.handles:
            await handle.close()
        try:
            await self.wait_for_termination()
        except asyncio.TimeoutError:
            try:
                self.process.kill()
            except ProcessLookupError:
                pass
        self.running.clear()


async def run_simple(
        program: Program,
        configuration: pathlib.Path,
        source: str,
        timeout: float = 300):
    async with Runner(program, [configuration]) as runner:
        await runner.wait_for_handles()
        start = time.monotonic()
        runner.handles[0].writer.write(source.encode('utf-8'))
        runner.handles[0].writer.write_eof()
        result = await asyncio.wait_for(runner.handles[0].reader.read(), timeout)
        duration = time.monotonic() - start
        return result, duration

# -*- coding:utf-8 -*-
#
# Copyright (C) 2019, Maximilian Köhl <mkoehl@cs.uni-saarland.de>

from __future__ import annotations

from typing import List

import asyncio
import importlib.util
import pathlib
import time
import traceback

from .runner import Handle
from .printer import Page, load_pages


STARTUP_DELAY = 2

config_directory = pathlib.Path(__file__).absolute().parent.parent / 'tests' / 'config'


def add_configuration(name: str):
    configuration = config_directory / f'{name}.json'

    def decorator(function):
        if not hasattr(function, '__test_configurations'):
            function.__test_configurations = []
        function.__test_configurations.append(configuration)
        return function

    return decorator


def get_configuration(name: str):
    return config_directory / f'{name}.json'


def may_fail(message: str):
    def decorator(function):
        function.__test_may_fail = message
        return function
    return decorator


bonus_only = may_fail('You have to pass this test only if you want the bonus!')


async def run_with_runner(program, member):
    from .runner import Runner
    async with Runner(program, member.__test_configurations) as runner:
        await runner.wait_for_handles()
        await asyncio.sleep(STARTUP_DELAY)
        start = time.monotonic()
        await member(runner, *runner.handles)
    print('Duration:', time.monotonic() - start)


async def expect_pages(handle: Handle, pages: List[Page], *, timeout: float = 200):
    number = 0
    async for page in handle.iter_pages(timeout):
        assert page == pages[number], f'mismatch on page {number}'
        if number == len(pages) - 1:
            return
        number += 1
    raise AssertionError(f'expected {len(pages)} but got {number}')


def load_the_end():
    directory = pathlib.Path(__file__).absolute().parent.parent / 'tests' / 'the-end'

    source = (directory / 'input.txt').read_bytes()
    reference = load_pages((directory / 'default.out').read_bytes())

    return source, reference


def load_book(name: str):
    books_directory = pathlib.Path(__file__).absolute().parent.parent / 'tests' / 'books'
    book_directory = books_directory / name

    source = (book_directory / 'input.txt').read_bytes()
    reference = load_pages((book_directory / 'default.out').read_bytes())

    return source, reference


def main(arguments):
    cwd = pathlib.Path('.').absolute()

    if arguments.executable.name.endswith('.jar'):
        program = ['java', '-jar', arguments.executable.absolute()]
    else:
        program = [arguments.executable.absolute()]

    for test in arguments.test:
        for test_file in cwd.glob(test):
            module_name = test_file.stem
            spec = importlib.util.spec_from_file_location(module_name, str(test_file))
            module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(module)
            for name, member in module.__dict__.items():
                if name.startswith('test_') and callable(member):
                    try:
                        print(f'Run test {module.__name__}.{name}.')
                        if hasattr(member, '__test_configurations'):
                            res = run_with_runner(program, member)
                        else:
                            res = member(program)
                        if asyncio.iscoroutine(res):
                            loop = asyncio.get_event_loop()
                            loop.run_until_complete(res)
                        print('Ok!')
                    except asyncio.TimeoutError:
                        if hasattr(member, '__test_may_fail'):
                            print('Timeout!', member.__test_may_fail)
                        else:
                            print('Timeout!')
                    except Exception as error:
                        traceback.print_exc()
                        traceback.print_tb(error.__traceback__)
                        if hasattr(member, '__test_may_fail'):
                            print('Failed!', member.__test_may_fail)
                        else:
                            print('Failed!')

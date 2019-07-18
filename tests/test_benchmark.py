# -*- coding:utf-8 -*-
#
# Copyright (C) 2019, Maximilian Köhl <mkoehl@cs.uni-saarland.de>

from __future__ import annotations

import hashlib

from sidekick import tester
from sidekick.runner import Handle, Runner


CHUNK = '''
His man-ner was not ef-fus-ive. It sel-dom was; but he was glad, I think,
to see me. With hardly a word spoken, but with a kindly eye, he waved
me to an arm-chair, threw across his case of ci-gars, and in-dic-ated a
spirit case and a gas-o-gene in the corner. Then he stood be-fore the fire
and looked me over in his sin-gu-lar in-tro-spect-ive fash-ion.
'''.strip() * 50

HASH = '8e62798e2f857491a3e54cdb511de48ae8100a01'


@tester.add_configuration('slow')
async def test_benchmark(runner: Runner, handle: Handle):
    for _ in range(10):
        for _ in range(50):
            handle.chunk(CHUNK)
            handle.end_of_paragraph()
        handle.end_of_segment()
    handle.eof()

    assert hashlib.sha1(await handle.drain()).hexdigest(), (
        'sha1 mismatch: incorrect result'
    )

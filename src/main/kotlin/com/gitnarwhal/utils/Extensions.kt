package com.gitnarwhal.utils

import java.nio.file.Path

fun String.toPath() = Path.of(this)
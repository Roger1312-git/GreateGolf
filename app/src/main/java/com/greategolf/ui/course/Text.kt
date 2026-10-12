package com.greategolf.ui.course

import java.text.Normalizer

/** Lower-case, accent-free text for forgiving Vietnamese search. */
fun fold(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace("\\p{M}".toRegex(), "").replace('đ', 'd')

# Data preflight notes

The first generator-only check rejected unequal paired BPE lengths before
writing a dataset. Cause: action text moved between quoted and unquoted
contexts, changing leading-space BPE merges. Both alternative action phrases
now use the same guillemet boundaries. This preserves the intended semantics
and permits a strict equality assertion for every contrasting token length.
This was a data-construction correction, with zero model inference or updates.

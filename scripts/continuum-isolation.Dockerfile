# Empty runtime; tool binaries are explicit read-only mounts, no downloads.
FROM scratch
USER 65534:65534
WORKDIR /work
ENTRYPOINT ["/usr/bin/env", "-i"]

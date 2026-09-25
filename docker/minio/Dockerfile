# The final MinIO community security release was not published to the public
# container registries. Build the pinned upstream tag as its release notes
# prescribe instead of silently falling back to an older vulnerable image.
FROM golang:1.24.8-alpine3.22 AS builder

ARG MINIO_VERSION=RELEASE.2025-10-15T17-29-55Z
ARG MINIO_BUILD_VERSION=2025-10-15T17:29:55Z
ARG MINIO_COMMIT=9e49d5e7a648
RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 go install -trimpath \
    -ldflags="-s -w \
      -X github.com/minio/minio/cmd.Version=${MINIO_BUILD_VERSION} \
      -X github.com/minio/minio/cmd.CopyrightYear=2025 \
      -X github.com/minio/minio/cmd.ReleaseTag=${MINIO_VERSION} \
      -X github.com/minio/minio/cmd.CommitID=${MINIO_COMMIT} \
      -X github.com/minio/minio/cmd.ShortCommitID=${MINIO_COMMIT}" \
    github.com/minio/minio@${MINIO_VERSION}

FROM alpine:3.22

RUN apk add --no-cache ca-certificates \
    && addgroup -S minio \
    && adduser -S -G minio -h /data minio \
    && mkdir -p /data \
    && chown minio:minio /data

COPY --from=builder /go/bin/minio /usr/local/bin/minio

USER minio
VOLUME ["/data"]
EXPOSE 9000 9001
ENTRYPOINT ["minio"]
CMD ["server", "/data", "--console-address", ":9001"]

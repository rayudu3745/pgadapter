FROM us-docker.pkg.dev/artifact-foundry-prod/docker-3p-trusted/alpine:latest

ARG BUILD_DATE

LABEL build_date=$BUILD_DATE

CMD ["echo", "Hello, World! (Built: $BUILD_DATE)"]

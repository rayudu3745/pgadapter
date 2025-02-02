FROM busybox:latest

ARG BUILD_DATE

LABEL build_date=$BUILD_DATE

CMD ["echo", "Hello, World! (Built: $BUILD_DATE)"]

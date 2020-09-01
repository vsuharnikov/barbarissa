## Build

```shell script
sbt "clean;docker"
```

## Run

```shell script
docker run --name barbarissa -m 256M -d -p 10203:10203 \
-v /local/path:/var/lib/barbarissa \
vsuharnikov/barbarissa-backend:latest
```

## API

See at http://127.0.0.1:10203/docs

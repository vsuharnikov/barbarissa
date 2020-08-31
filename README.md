## Build

```shell script
sbt "clean;docker"
```

## Run

```shell script
docker run --name barbarissa -d -p 10203:10203 \
-v /local/path:/var/lib/barbarissa \
vsuharnikov/barbarissa-backend:0.0.1
```

## API

See at http://127.0.0.1:10203/docs

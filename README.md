# gpml-sync-action

Build the jar:
```
ant build
```

Run the jar:
```
java -jar build/SyncAction.jar 20230101000000
```

The arg is a date stamp with the format: YYYYMMDDHHMMSS. You can generate a date stamp for n days prior to the current date with the following Unix command, e.g., for GH Actions:
```
$(date --utc +%Y%m%d%H%M%S -d "1 day ago")
```

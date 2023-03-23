# gpml-sync-action

### Build the jar:
```
ant build
```

### Run the jar:

You have two options to run the sync action:

**1) Regular syncing**

Pull in recent changes on classic site since a specific timepoint

```
java -jar build/SyncAction.jar 20230101000000
```

The arg is a date stamp with the format: YYYYMMDDHHMMSS. You can generate a date stamp for n days prior to the current date with the following Unix command, e.g., for GH Actions:
```
$(date --utc +%Y%m%d%H%M%S -d "1 day ago")
```


**2) Update list of pathways**

Reads list of identifiers from file (one WP identifier per line) and syncs their latest revision from the classic site

```
java -jar build/SyncAction.jar pathways.txt
```

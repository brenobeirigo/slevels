# Mobility-on-demand simulator
## Transportation network server
### Starting the server

Node ids and shortest distances are pulled from a REST server.
This server have to be initialized using the same travel
network the experiments are running.

Access file `InstanceConfig.java` and attribute instance name to the `source` variable.

## Configuration
The file `config.json` configures witch instance will be loaded and how much information will be printed on the screen each
round. Example:

    {
      "instance_file_path": "C:\\Users\\LocalAdmin\\IdeaProjects\\slevels\\src\\main\\resources\\week\\profile_time.json",
      "info_level": "show_all_info"
    }

If file cannot be accessed, the instance at `resources\default_instance.json` is loaded, and a the summary of round
information is printed.

The information levels can be set using the following labels:


|Label|Information|
|-----|-----------|
|`print_all_round_info`| Shows round stats and detailed vehicle routes.|
|`print_no_round_info`| Ommit all round info.|
|`print_summary_round_info`| Only shows rond stats.|

## Rebalancing strategies

|Label|Description|
|-----|-----------|
|`rebalance_heuristic`||
|`rebalance_optimal_alonso_mora`| | 
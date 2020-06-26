# Mobility-on-demand simulator
## Transportation network server
### Starting the server

Node ids and shortest distances are pulled from a REST server.
This server have to be initialized using the same travel
network the experiments are running.

Access file `InstanceConfig.java` and attribute instance name to the `source` variable.

## Installation

Import the Gurobi `.jar` library from `C:\gurobi810\win64\lib` to execute optimization models.

## Configuration

The file `config.json` configures witch instance will be loaded and how much information will be printed on the screen each
round. Example:

    {
      `instance_file_path`: `C:\\Users\\LocalAdmin\\IdeaProjects\\slevels\\src\\main\\resources\\week\\profile_time.json`,
      `info_level`: `show_all_info`
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

## Logging

|Label|Description|
|-----|-----------|
|`save_vehicle_round_geojson`| Save each vehicle journey in a different file  in folder `geojson_track`.|
|`save_request_info_csv`| Save request service info  in folder `request_track`.|
|`save_round_info_csv`| Save round service info in folder `round_track`.|
|`show_all_vehicle_journeys`|Print detailed journey upon finishing the execution.|
|`show_round_fleet_status`| What each car is doing (visit and passengers)|
|`show_round_info`| User service levels and fleet status.|

## Vehicle journeys
    
    ########################################################################################
    ######   V945 [   0,1513] => 1483 (work) +   30 (wait) = 1513 (98.02%) #### PK:   282  DP:   629
    ########################################################################################
      OR945 (---- |    0 -   30 | ----) [----]
    Travel time:      28
        Waiting:       0
       PK60 (  19 |   58 -   58 |  619) [  39]
    Travel time:     311
        Waiting:       0
       DP60 ( 330 |  369 -  369 | 1230) [  39]
    Travel time:      90
        Waiting:       0
      PK543 ( 333 |  459 -  459 |  633) [ 126]
    Travel time:      78
        Waiting:       0
      PK701 ( 420 |  537 -  537 |  720) [ 117]
    Travel time:     218
        Waiting:       0
      DP701 ( 638 |  755 -  755 | 1238) [ 117]
    Travel time:     758
        Waiting:       0
      DP543 (1040 | 1513 - 1513 | 1640) [ 473]
    Travel time:       0
        Waiting:       0
      ST543 (---- | 1513 - 3600 | ----) [----]
     Path: [[-73.951427, 40.785196],[-73.983180, 40.750341],[-73.983180, 40.750341],[-73.988753, 40.745289],[-73.988753, 40.745289],[-73.937425, 40.804385],[-73.937425, 40.804385],[-73.970915, 40.748406],[-73.970915, 40.748406],[-73.983887, 40.713201],[-73.983887, 40.713201],[-73.928206, 40.863614],[-73.928206, 40.863614],[-73.928206, 40.863614]]

## Duration X Distance

The distance between points can be derived from two sources in the `.csv` configuration file:
1) `distances_file` - Distance matrix in meters created from network graph. Conversion to durations is done using 
SPEED parameter.
2) `durations_file` - Duration matrix (considering 30Km/h) in seconds created from network graph.

Working with the duration matrix is more stable because we guarantee that the sum of the durations of every leg throughout 
the shortest path from `o` to `d` will be equal to the total duration from  `o` to `d`.
This guarantee cannot be made for the distance matrix due to accumulated approximation errors.
The sum of individual leg durations (converted from leg lengths) can be different from the converted total distance.
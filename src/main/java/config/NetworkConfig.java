package config;

public record NetworkConfig(
        String distances_file,
        String zone_data_file,
        String network_node_info_file,
        String adjacency_matrix_file,
        String shortest_path_method,
        Double avg_speed_km_hour){};


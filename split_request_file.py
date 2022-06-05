import pandas as pd
import os
import matplotlib.pyplot as plt

def plot_count(df_requests, date_start, date_end, max_passenger=4, ax=None):
    print(ax)
    filter_pu_within_range = df_requests["pickup_datetime"].between(date_start, date_end, inclusive="left")
    filter_passenger_count = df_requests["passenger_count"] <= max_passenger
    df_subset_requests = df_requests[filter_pu_within_range & filter_passenger_count]
    df_subset_requests = df_subset_requests[["pickup_datetime", "passenger_count"]]
    print("plotting...")
    df_subset_requests.set_index("pickup_datetime").resample("1T").sum().plot(ax=ax)
    
def split_into_ranges(datetime_range_list, filepath_requests=None, df_requests=None, output_folder=None):
    n_ranges = len(datetime_range_list)
    print(f"Processing {n_ranges} ranges...")
    fig, axes = plt.subplots(nrows=1, ncols=n_ranges, sharey=True, figsize=(n_ranges*5, 5))
    ax_ranges = zip(axes, datetime_range_list)
    # for a,(b,c) in ax_ranges:
    #     print(a,b,c)
    
    if df_requests is None:
        df_requests = pd.read_csv(filepath_requests, parse_dates=["pickup_datetime", "dropoff_datetime"])
        
    for ax, (date_start, date_end) in ax_ranges:
        print(f"Selecting range {date_start} - {date_end}")
        
        filter_pu_within_range = df_requests["pickup_datetime"].between(date_start, date_end, inclusive="left")
        df_subset_requests = df_requests[filter_pu_within_range]
        
        plot_count(df_subset_requests, date_start, date_end, max_passenger=4, ax=ax)
        
        filename_excerpt = f"{date_start}-{date_end}_{os.path.basename(filepath_requests)}"
        
        if output_folder is None:
            output_folder = os.path.dirname(filepath_requests)
        
        filepath_excerpt = os.path.join(output_folder, filename_excerpt)
        print(f"Savint to '{filepath_excerpt}'...")
        
        df_subset_requests.to_csv(filepath_excerpt)
        
        
        
        
        
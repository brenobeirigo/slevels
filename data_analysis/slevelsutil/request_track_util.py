from collections import defaultdict

import numpy as np
import pandas as pd


def read_request_track(folder, request_track_experiment_label):
    experiment_path = "{}request_track/{}.csv".format(folder, request_track_experiment_label)
    df = pd.read_csv(experiment_path, parse_dates=True, comment="#")
    return df


def concat_dfs(user_track_dfs, method_labels):
    df_compare_list = []

    for method_label, df_user_track in zip(method_labels, user_track_dfs):
        df_delay = df_user_track[["delay_pk", "class", "service", "service_level"]].copy()
        df_delay["method"] = method_label
        df_compare_list.append(df_delay)

    df_compare = pd.concat(df_compare_list)

    return df_compare


def add_ticks(ax, start, end, step, label_min=None, label_max=None, label_step=None, tick_format=None):
    if label_min is not None:
        y_tick_labels = np.arange(label_min, label_max, label_step)
    else:
        y_tick_labels = np.arange(start, end, step)

    if tick_format is not None:
        y_tick_labels = [tick_format.format(tick) for tick in y_tick_labels]

    ax = ax.set(
        yticks=np.arange(start, end, step),
        yticklabels=y_tick_labels
    )

    return ax


def add_labels(df_user_track, dict_sl_class, dict_slevel, dict_fleet, headers):
    df_user_track = df_user_track.rename(index=str, columns=headers)
    sq_categories = list(dict_sl_class.values())
    category_segmentation = pd.api.types.CategoricalDtype(categories=sq_categories, ordered=True)
    df_user_track[headers["class"]].replace(dict_sl_class, inplace=True)
    df_user_track[headers["service_level"]].replace(dict_slevel, inplace=True)
    df_user_track[headers["service"]].replace(dict_fleet, inplace=True)
    return df_user_track


def value_counts_dict(df, slclass, header_method, header_class):
    df_class = df[df[header_class] == slclass]
    counts = dict(df_class[header_method].value_counts())
    return counts


'''
Example of output: 
    
            Status      Method     Class   Count
    0      Serviced  Status quo  Business   30546
    6      Rejected  Status quo  Business    4485
    17   First-tier  Enforce SL  Low-cost   15596
    18  Second-tier  Status quo  Business   19209
'''


def get_dict_method_status(dict_sl_class, request_track_experiment_labels, dict_status, headers):
    header_method = headers["method"]
    header_class = headers["class"]

    dict_methods_class = defaultdict(list)
    for status, df in dict_status.items():
        for method in request_track_experiment_labels:
            for sl_class in dict_sl_class.values():
                dict_methods_class[headers["status"]].append(status)
                dict_methods_class[headers["method"]].append(method)
                dict_methods_class[headers["class"]].append(sl_class)
                user_count_method_status = value_counts_dict(df, sl_class, header_method, header_class).get(method, 0)
                dict_methods_class[headers["status_count"]].append(user_count_method_status)

    return dict_methods_class

# Python graphs: https://python-graph-gallery.com/
# Visualization with matplotlib: https://www.oreilly.com/library/view/python-data-science/9781491912126/ch04.htmlimport matplotlib.pyplot as plt
import os
from datetime import datetime, timedelta
from pprint import pprint

import matplotlib.pyplot as plt
import pandas as pd
import seaborn as sns
from matplotlib.offsetbox import AnchoredText
from scipy.ndimage.filters import gaussian_filter1d


N_PARKED_VEHICLES = "parked_vehicles"

def get_total_runtime(df):
    time_ride_matching_s = df["time_ride_matching_s"].sum()
    time_update_fleet_status_s = df["time_update_fleet_status_s"].sum()
    time_vehicle_rebalancing_s = df["time_vehicle_rebalancing_s"].sum()

    total_runtime = time_ride_matching_s + time_update_fleet_status_s + time_vehicle_rebalancing_s

    return total_runtime


def get_avg_runtime(df):
    time_ride_matching_s = df["time_ride_matching_s"]
    time_update_fleet_status_s = df["time_update_fleet_status_s"]
    time_vehicle_rebalancing_s = df["time_vehicle_rebalancing_s"]

    df = pd.DataFrame()
    df["runtime"] = time_ride_matching_s + time_update_fleet_status_s + time_vehicle_rebalancing_s
    runtime_avg = float(df["runtime"].mean())
    return runtime_avg


def get_results_dic(path_experiment, name_experiment):
    capacity_vehicle_initial_fleet = 4
    # Initial capacity to subtract from total number of seats
    # TODO add hiring info to instance, here you check the name only
    if "ERTV" in name_experiment:
        n_initial_fleet = 1000
    else:
        n_initial_fleet = 0

    capacity_initial_fleet = capacity_vehicle_initial_fleet * n_initial_fleet

    # Load results
    experiment_file = "{}round_track/{}.csv".format(path_experiment, name_experiment)

    # print("Processing experiment file '{}'".format(experiment_file))

    df = pd.read_csv(experiment_file, index_col="timestamp", parse_dates=True)
    pprint(df.columns)

    # Number of requests
    total_requests = df["n_requests"].sum()
    serviced = df["finished"][-1]
    denied = df["denied"][-1]

    # Service quality
    sq_classes = ["A", "B", "C"]
    sq_settings = ["pk", "dp", "count", "unmet_slevels"]
    # e.g., ['A_pk', 'A_dp', 'A_count', 'A_unmet_slevels', 'B_pk', 'B_dp', 'B_count', 'B_unmet_slevels', 'C_pk', 'C_dp', 'C_count', 'C_unmet_slevels']
    sq_user_labels = ["{}_{}".format(sq_class, sq_setting) for sq_class in sq_classes for sq_setting in sq_settings]
    sq_user_dic = {l: df[l][-1] for l in sq_user_labels}

    # Distance traveled
    distance_cruising = df["distance_traveled_cruising"][-1]
    distance_loaded = df["distance_traveled_loaded"][-1]
    distance_rebalancing = df["distance_traveled_rebalancing"][-1]
    distance_total = distance_cruising + distance_loaded + distance_rebalancing

    # Pickup
    avg_pk_delay = df["pk_delay"].mean()
    avg_ride_delay = df["total_delay"].mean()

    # Separate occupancy labels (e.g., O1, O2, O3, etc.)
    occupancy_labels = [col for col in list(df) if 'O' in col]
    status_labels = ["idle", "picking_up"] + occupancy_labels

    # Get fleet makeup
    # fleet_makeup_labels = [col for col in list(df) if 'V' in col]
    # fleet_makeup = {mk:df[mk][-1] for mk in fleet_makeup_labels}
    # total_seats = {"seats_" + k:int(k[1:]) * v for k,v in fleet_makeup.items()}
    # total_seats["total_seats"] = sum(total_seats.values())

    # FleetConfig statistics
    avg_seats = df["total_capacity"].mean() - capacity_initial_fleet
    median_seats = df["total_capacity"].median() - capacity_initial_fleet
    max_seats = df["total_capacity"].max() - capacity_initial_fleet
    id_max_seats = df["total_capacity"].idxmax()
    max_hired = df["hired_vehicles"].max()
    median_hired = df["hired_vehicles"].median()
    mean_hired = df["hired_vehicles"].mean()
    mean_active = df["active_vehicles"].mean()
    occupancy = (df["seat_count"] / df['total_capacity']).mean()

    # How many vehicles are active (i.e., servicing customers)?
    total_occupied = (df["O1"] + df["O2"] + df["O3"] + df["O4"])
    o1 = (df["O1"] / total_occupied).mean()
    o2 = (df["O2"] / total_occupied).mean()
    o3 = (df["O3"] / total_occupied).mean()
    o4 = (df["O4"] / total_occupied).mean()

    # How many vehicle of each capacity hired?
    total_hired = (df["V1"] + df["V2"] + df["V3"] + (df["V4"]) - n_initial_fleet)
    v1 = (df["V1"] / total_hired).mean()
    v2 = (df["V2"] / total_hired).mean()
    v3 = (df["V3"] / total_hired).mean()
    v4 = ((df["V4"] - n_initial_fleet) / total_hired).mean()

    idle_mean = df['idle'].mean()
    idle_pickingup = df['picking_up'].mean()

    servicing_seats = (df['seat_count'] / df["total_capacity"]).mean()
    picking_up_seats = (df['picking_up_seats'] / df["total_capacity"]).mean()
    rebalancing_seats = (df['rebalancing_seats'] / df["total_capacity"]).mean()
    parked_seats = (df['empty_seats'] / df["total_capacity"]).mean()

    # Filter occupancy columns
    df_occupancy = df[occupancy_labels]

    # Build fleet status
    df_status = pd.DataFrame(df_occupancy)
    df_status["picking_up"] = df["picking_up"]
    df_status["idle"] = df["idle"]

    # Smooth values
    df_occupancy = df_occupancy.rolling(window=24).mean()

    # Runtime
    total_runtime = get_total_runtime(df)
    avg_runtime = get_avg_runtime(df)

    columns = ['waiting', 'finished', 'denied', 'n_requests', 'seat_count',
               'picking_up_seats', 'rebalancing_seats', 'empty_seats',
               'total_capacity', 'active_vehicles', 'hired_vehicles',
               'deactivated_vehicles', 'enroute_count', 'pk_delay', 'total_delay',
               'parked_vehicles', 'origin_vehicles', 'rebalancing',
               'stopped_rebalancing', 'idle', 'picking_up', 'O1', 'O2', 'O3', 'O4',
               'V1', 'V2', 'V3', 'V4', 'distance_traveled_cruising',
               'distance_traveled_loaded', 'distance_traveled_rebalancing',
               'time_ride_matching_s', 'time_update_fleet_status_s',
               'time_vehicle_rebalancing_s', 'A_pk', 'A_dp', 'A_count',
               'A_unmet_slevels', 'B_pk', 'B_dp', 'B_count', 'B_unmet_slevels', 'C_pk',
               'C_dp', 'C_count', 'C_unmet_slevels']
    # Dictionary of agreggate data
    dic_agreggate_data = {
        "serviced_seats": "{:.2%}".format(servicing_seats),
        "picking_up_seats": "{:.2%}".format(picking_up_seats),
        "rebalancing_seats": "{:.2%}".format(rebalancing_seats),
        "parked_seats": "{:.2%}".format(parked_seats),
        "serviced": "{:.2%}".format(serviced / total_requests),
        "denied": "{:.2%}".format(denied / total_requests),
        'max_hired': max_hired,
        'occupancy': "{:.2%}".format(occupancy),
        'o1': "{:.2%}".format(o1),
        'o2': "{:.2%}".format(o2),
        'o3': "{:.2%}".format(o3),
        'o4': "{:.2%}".format(o4),
        'v1': "{:.2%}".format(v1),
        'v2': "{:.2%}".format(v2),
        'v3': "{:.2%}".format(v3),
        'v4': "{:.2%}".format(v4),
        'mean_hired': "{:.2f}".format(mean_hired),
        'median_hired': median_hired,
        'avg_seats': "{:.2f}".format(avg_seats),
        'max_seats': max_seats,
        'id_max_seats': id_max_seats,
        'median_seats': median_seats,
        'mean_active': mean_active,
        "total_requests": total_requests,
        "avg_pk_delay": "{:.2f}".format(avg_pk_delay),
        "avg_ride_delay": "{:.2f}".format(avg_ride_delay),
        "total_runtime_s": "{:.2f}".format(total_runtime),
        "avg_runtime_s": "{:.2f}".format(avg_runtime),
        "distance_cruising": "{:.2%}".format(distance_cruising / distance_total),
        "distance_loaded": "{:.2%}".format(distance_loaded / distance_total),
        "distance_rebalancing": "{:.2%}".format(distance_rebalancing / distance_total),
        "distance_total": distance_total // 1000
    }

    # All data
    ##dic_ag = {**dic_agreggate_data, **fleet_makeup}
    ##dic_ag = {**dic_ag, **total_seats}
    # dic_ag = {**dic_ag, **sq_user_dic}
    return dic_agreggate_data


def plot_vehicle_status_graph(
        instances_folder,
        result_folder,
        name_experiment,
        replace=True,
        smooth=None,
        print_details=True,
        fontsize='small',
        fontsize_label='small',
        show_tick_label_x=False,
        label_y=(None, None, 'small'),
        horizontal_legend=False,
        fig_type='png',
        custom_tw=(None, None),
        day_sep_config=(None, None),
        size_inches=(18, 3),
        nbins_y=5,
        x_data_format=('1h', '%H'),
        color_bg="#FFE4E1",
        context_font_scale=1,
        print_details_axis=(None, None),
        input_dic=None,
        show_week_days=False,
        show_day_separator=True,
        linewidth=1.5,
        tick_length=3,
        show_service_rate="Service rate: ",
        fontsize_label_sr='large',
        msg=None,
        annotate_data_per_status=False,
        extra_info_coord = '2019-02-01 18:30:00'):
    # Create directory
    if not os.path.exists(result_folder):
        os.makedirs(result_folder)

    # Save path
    name_fig = "{}{}.{}".format(result_folder, "VS_" + name_experiment, fig_type)

    # Stop generation if already generated
    if os.path.isfile(name_fig) and not replace:
        return

    print("Creating figure in '{}'".format(name_fig))

    # Get experiment data
    if input_dic:
        srate_dict = {'S1': 'High', 'S2': 'Moderate', 'S3': 'Low'}
        fleet_size = int(input_dic["max_capacity"])
        v_cap = int(input_dic["max_capacity"])
        th = int(input_dic["simulation_time"])
        tw = int(input_dic["batch_duration"])
        cs = input_dic["customer_segmentation"]
        sr = input_dic.get("service_rate", "NA")

        # Standard data frequency
    # E.g., freq = {10min, 6h, 1d}, format = {'%H:%M', '%H'}
    x_freq, x_format = x_data_format

    # Get custom time window (.e.g., ('2011-02-01T00:00:00', '2011-02-06T00:00:00'))
    custom_tw_min, custom_tw_max = custom_tw
    print(f"TW1({custom_tw_min}, {custom_tw_max})")

    print("TW:", custom_tw)
    # X series (dates)
    x_min = datetime.strptime(custom_tw_min, '%Y-%m-%dT%H:%M:%S')
    x_max = datetime.strptime(custom_tw_max, '%Y-%m-%dT%H:%M:%S')

    print(f"TW2({custom_tw_min}, {custom_tw_max})")

    x_start = x_min + timedelta(seconds=tw)
    x_end = x_max + timedelta(seconds=tw)

    x_last_req = x_min + timedelta(seconds=th) + timedelta(seconds=tw)

    range_dates = pd.date_range(start=x_start,
                                end=x_end,
                                freq=x_freq)

    # range_dates_label = [str(int(d.strftime(x_format))) for d in range_dates]
    range_dates_label = [str(d.strftime(x_format)) for d in range_dates]
    # range_dates_label = [str(int(d.strftime(config[th]['format'])))+'h' for d in range_dates]

    # Load results
    df = pd.read_csv("{}{}.csv".format(instances_folder, name_experiment),
                     index_col="timestamp",
                     parse_dates=True,
                     comment="#")

    print(df.columns)
    print("Firt record:", df.iloc[0].name, " -- Last record:", df.iloc[-1].name)

    print("TW:", x_min, x_max)

    # Number of requests
    total_requests = df["n_requests"].sum()
    time_ride_matching_s = df["time_ride_matching_s"].sum()
    time_update_fleet_status_s = df["time_update_fleet_status_s"].sum()
    time_vehicle_rebalancing_s = df["time_vehicle_rebalancing_s"].sum()

    total_runtime = time_ride_matching_s + time_vehicle_rebalancing_s + time_vehicle_rebalancing_s
    serviced = df["finished"][-1]
    denied = df["denied"][-1]

    # Separate occupancy labels (e.g., O1, O2, O3, etc.)
    occupancy_labels = [col for col in list(df) if 'O' in col]
    status_labels = [N_PARKED_VEHICLES, "rebalancing",
                     "picking_up"] + occupancy_labels  # ["origin_vehicles", N_PARKED_VEHICLES, "rebalancing", "picking_up"] + occupancy_labels

    # Get fleet makeup
    fleet_makeup_labels = [col for col in list(df) if 'V' in col]
    fleet_makeup = {mk: df[mk][-1] for mk in fleet_makeup_labels}
    total_seats = {k: int(k[1:]) * v for k, v in fleet_makeup.items()}

    # Filter occupancy columns
    df_occupancy = df[occupancy_labels]

    # Build fleet status
    df_status = pd.DataFrame(df_occupancy)
    df_status["picking_up"] = df["picking_up"]
    df_status["rebalancing"] = df["rebalancing"]
    df_status[N_PARKED_VEHICLES] = df[N_PARKED_VEHICLES] + df["origin_vehicles"]

    # Smooth values
    df_occupancy = df_occupancy.rolling(window=24).mean()

    # Define axis
    df_filtered = df_status.loc[x_start:x_end]
    x = df_filtered.index.values
    print("Len x:", len(x))

    y = [df_filtered[c].tolist() for c in status_labels]

    # Define limits of y (before smoothing)
    ymin = 0
    ymax = max(y)

    # Smoth lines in y
    if smooth:
        y = gaussian_filter1d(y, sigma=smooth)

    # Get the unique hours within interval()
    hours = set(df_occupancy.index.hour.values)

    # Format legend
    graph_legend = ["P" + str(i) for i in range(1, len(occupancy_labels) + 1)]
    # graph_legend = ["Parked", "Rebalancing", "Picking up"] + graph_legend #["Origin", "Parked", "Rebalancing", "Picking up"] + graph_legend
    graph_legend = ["Parked", "Rebalancing", "Picking up"] + ["1 passenger", "2 passengers", "3 passengers",
                                                              "4 passengers", "5 passengers", "6 passengers"]
    # Choosing palette. Source: https://seaborn.pydata.org/tutorial/color_palettes.html

    sns.set_context('paper')
    sns.set_style("ticks", {"xtick.major.size": 8, "axes.grid": True, 'axes.facecolor': '#d0d0dd', 'grid.color': '1.0'})
    sns.set_context("notebook", font_scale=context_font_scale)
    print(sns.axes_style())

    # sns.set_context("poster", font_scale = .5, rc={"grid.linewidth": 0.6})
    # sns.set_style("ticks", rc={'grid.color': 'black', 'axes.facecolor': color_bg})
    # sns.set_palette("RdBu_r", len(graph_legend))

    # sns.set_palette("viridis", len(graph_legend))
    # ['#ffffd9','#edf8b1','#c7e9b4','#7fcdbb','#41b6c4','#1d91c0','#225ea8','#253494','#081d58']
    # ['#081d58', '#253494', '#225ea8', '#1d91c0', '#41b6c4', '#7fcdbb', '#c7e9b4', '#edf8b1', '#ffffd9']
    # sns.set_palette("YlGnBu_r", len(graph_legend))

    # YlGnBu_r = ['#081d58', '#e31a1c', '#225ea8', '#1d91c0', '#41b6c4', '#7fcdbb', '#c7e9b4', '#edf8b1', '#ffffd9']
    YlGnBu_r = ['#081d58', '#e31a1c', '#225ea8', '#1d91c0', '#7fcdbb', '#c7e9b4', '#ffffd9']
    # YlGnBu_r = ['#081d58', '#e31a1c', '#225ea8', '#1d91c0', '#41b6c4', '#7fcdbb', '#edf8b1', '#ffffd9']

    # YlGnBu_r = ['#ffffcc','#c7e9b4','#7fcdbb','#41b6c4','#2c7fb8','#253494']
    # YlGnBu_r = ['#081d58', '#e31a1c', '#225ea8', '#41b6c4', '#7fcdbb', '#c7e9b4', '#ffffcc']
    # fresh_cut_day = ["#0B486B", "#40C0CB", "#F9F2E7", "#AEE239", "#8FBE00"]
    # fresh_cut_day_4 = ["#0B486B", "#F9F2E7", "#AEE239", "#8FBE00"]
    # pallete = adrift_in_dreams_4
    sns.set_palette(YlGnBu_r)

    print("Range: ", range_dates)
    plt.xlim(min(x), range_dates[-1])

    # Set the y limits making the maximum 5% greater
    # plt.ylim(ymin, ymax)

    # Pickup
    avg_pk_delay = df["pk_delay"].mean()
    avg_ride_delay = df["total_delay"].mean()

    # Plot
    # plt.stackplot(x, y, labels=graph_legend, linewidth=0.01, edgecolor='white')
    plt.stackplot(x, y, labels=graph_legend, linewidth=0.00)  # , alpha = 0)#, edgecolor='white')

    # Line plot
    #     for line_y, c  in zip(y, YlGnBu_r):
    #         print(len(x), len(line_y))
    #         plt.plot(x, line_y, color=c, linewidth=1.00)#, labels=graph_legend, linewidth=0.00)#, alpha = 0)#, edgecolor='white')

    # Define and format
    # https://matplotlib.org/api/_as_gen/matplotlib.pyplot.xticks.html
    # {size in points, 'xx-small', 'x-small', 'small', 'medium', 'large', 'x-large', 'xx-large'}
    ticks_x = []
    if show_tick_label_x:
        ticks_x = range_dates_label

    l_y, t_y, fontsize = label_y

    if l_y is not None:
        plt.yticks(l_y, t_y, fontsize=fontsize)
        plt.ylim(l_y[0], l_y[-1])
        print("LIMITS:", l_y[0], l_y[-1])
    else:
        plt.yticks(fontsize=fontsize)
        plt.locator_params(axis='y', nbins=nbins_y)

    plt.xticks(range_dates, ticks_x, fontsize=fontsize)

    # https://matplotlib.org/api/axis_api.html#matplotlib.axis.Axis.set_tick_params
    ax = plt.gca()
    t_width = linewidth
    ax.tick_params(width=t_width, length=tick_length)
    ax.spines['top'].set_linewidth(t_width)
    ax.spines['right'].set_linewidth(t_width)
    ax.spines['bottom'].set_linewidth(t_width)
    ax.spines['left'].set_linewidth(t_width)

    # Print x, y axis labels
    det_x, det_y = print_details_axis
    if det_x:
        plt.xlabel(det_x, fontweight='bold', fontsize=fontsize_label)
    if det_y:
        plt.ylabel(det_y, fontweight='bold', fontsize=fontsize_label)

    if print_details:

        # Position legend (Source: https://matplotlib.org/api/legend_api.html?highlight=legend#module-matplotlib.legend)
        legend = None
        # https://pythonspot.com/matplotlib-legend/
        if horizontal_legend:
            legend = plt.legend(loc="upper center",
                                bbox_to_anchor=(0.5, -0.1),
                                ncol=len(graph_legend),
                                fontsize=fontsize,
                                edgecolor="white",
                                title="Vehicle status:")
        else:
            legend = plt.legend(loc="upper left",
                                bbox_to_anchor=(1, 1),
                                ncol=1,
                                fontsize=fontsize,
                                edgecolor="white",
                                title="Vehicle status:")

        legend.get_title().set_fontsize(fontsize_label)  # legend 'Title' fontsize

    # Line defining TW
    # plt.axvline(x = x_last_req, linewidth=1, color='r', linestyle='--')

    if show_week_days:
        # First date week
        week_day = x_min + timedelta(hours=10)

        # Loop days of the week
        diff_days = x_end - x_start
        print("DAYS:", diff_days)
        print(diff_days.total_seconds(), diff_days.total_seconds() / (24 * 3600))

        for i in range(0, 7):
            # Print week day
            plt.text(week_day,
                     l_y[-1] + 50,
                     week_day.strftime("%a"),
                     fontsize=fontsize_label,
                     bbox=dict(boxstyle='square,pad=0.0',
                               fc='none',
                               ec='none'))

            week_day = week_day + timedelta(hours=24)

    ########################################################################
    # Inserting data labels ################################################

    # x position
    x_label = datetime.strptime(extra_info_coord, '%Y-%m-%d %H:%M:%S')
    val = [(sq_class, df_filtered.loc[extra_info_coord][sq_class]) for sq_class in status_labels]

    y_pos_labels = []
    previous = 0
    total = 0
    for status, v in val:
        total += v
        y_pos_labels.append((previous, v // 2 + previous, previous + v, v, status))
        previous = previous + v

    y_pos_labels.append((previous, v // 2 + previous, previous + v, total, 'total'))

    bar = []
    bottom = 0
    width_bar = 0.02

    if annotate_data_per_status:
        print("Data per status:")
        pprint(y_pos_labels)
        left = True
        color_line = 'blueviolet'
        for l, m, r, label, status in y_pos_labels:
            # bar.append(label)

            # ax.annotate(label, xy=(extra_info_coord, m-10), facecolor='red', alpha=0.5, edgecolor='red', textcoords='data', zorder= 1001)
            #         t = plt.text(x_label + timedelta(minutes=60), m, label, bbox=dict(boxstyle='square,pad=0.1',
            #                            alpha=0.8,
            #                            fc='white',
            #                            ec='none'),
            #                     zorder= 1001)
            if left == True:
                x_pos_tag = x_label + timedelta(minutes=60)
                side = ((x_label + timedelta(minutes=120), m))
            #             left = not left
            #         else:
            #             x_pos_tag = x_label - timedelta(minutes=60)
            #             side = ((x_label - timedelta(minutes=120) - timedelta(minutes=240), m))
            #             left = not left

            # print(left, side)
            print(side, (x_label, m + 30))
            ax.annotate("{} {}".format(label, status), xy=(x_label, m + 30),
                        xytext=side,
                        bbox=dict(
                            boxstyle='square,pad=0.1',
                            alpha=0.8,
                            fc='white',
                            ec='none'),
                        textcoords='data',
                        zorder=1001)  # ,
            # arrowprops=dict(arrowstyle='-', color='violet', linewidth=1))
            # arrowprops=dict(arrowstyle='->'), xytext=(15, -10)
            # t.set_bbox(dict(facecolor='red', alpha=0.5, edgecolor='red'))
            # plt.plot([x_label, x_label], [l,r], '-', linewidth=6, markersize=6, marker = "s", zorder= 2000)
            plt.plot([x_label, x_label], [l, r], linewidth=1, zorder=2000, markersize=6, marker="_", color=color_line)

            # plt.bar([x_label], (label,), width_bar, bottom = (bottom,), linewidth=0, zorder= 1000)
            # bottom += label

        # plt.axvline(x= x_label, linewidth=1, color='black', alpha=1)

    if show_day_separator:
        # Print day separator
        back_day_sep_dic, front_day_sep_dic = day_sep_config

        # Day of the week line separator
        dashed_line = x_min

        for i in range(0, 6):

            # Print day separator
            dashed_line = dashed_line + timedelta(hours=24)

            # if dashed_line != x_label:
            if back_day_sep_dic:
                back_day_sep_dic["x"] = dashed_line
                # White box below dashed line for constrast
                plt.axvline(**back_day_sep_dic)

            if front_day_sep_dic:
                front_day_sep_dic["x"] = dashed_line
                # Dashed line to separate week days
                plt.axvline(**front_day_sep_dic)

    # Remove white margins
    plt.margins(0, 0)

    # PRINT SERVICE RATE
    # String format - https://docs.python.org/2/library/string.html#formatstrings
    # font - https://matplotlib.org/gallery/text_labels_and_annotations/fonts_demo_kw.html
    # size - {size in points, 'xx-small', 'x-small', 'small', 'medium', 'large', 'x-large', 'xx-large'}

    if show_service_rate is not None:
        sr_pos_x = x_max
        sr_pos_y = t_y[-1]
        text = "{}{: >7.2%}".format(show_service_rate, serviced / total_requests)

        at = AnchoredText(text,
                          prop=dict(fontstyle='italic',
                                    fontsize=fontsize_label_sr,
                                    transform=ax.transAxes,
                                    bbox=dict(boxstyle='square,pad=0.1',
                                              alpha=0.6,
                                              fc='white',
                                              ec='none')), frameon=True, loc='upper right', pad=0.0, borderpad=0.3)

        at.patch.set_boxstyle("square,pad=0.0")
        at.patch.set_ec('none')
        at.patch.set_fc('none')
        ax.add_artist(at)

    if msg:
        msg_text, msg_fontsize = msg
        at = AnchoredText(msg_text,
                          prop=dict(fontstyle='italic',
                                    fontsize=msg_fontsize,
                                    transform=ax.transAxes,
                                    bbox=dict(boxstyle='square,pad=0.0',
                                              alpha=0.5,
                                              fc='none',
                                              ec='none')), frameon=True, loc='upper left', pad=0.0, borderpad=0.5)

        at.patch.set_boxstyle("square,pad=0.0")
        at.patch.set_ec('none')
        at.patch.set_fc('none')
        ax.add_artist(at)

    # Remove white margins
    plt.margins(0, 0)

    fig = plt.gcf()
    fig.set_size_inches(size_inches[0], size_inches[1])

    # plt.savefig(name_fig, bbox_inches="tight", pad_inches=0)
    plt.savefig(name_fig, bbox_inches="tight", dpi=300)

    # plt.text(x_max, 1000, "  INPUT:\n    FleetConfig size: {}({}) \n  Service rate: {:<10} \n  Segmentation: {}  \n   #Extensions: {} \n  Rebal. after: {}m \n    Drop after: {}m \n\n  OUTPUT:\n     #Requests: {} \n  Pickup delay: {:6.2f}s\n    Ride delay: {:6.2f}s \n       Runtime: {:6.2f}m".format(fleet_size, v_cap, sr, cs , ext, rebal*tw//60, rebal*tw*deact//60, serviced, avg_pk_delay, avg_ride_delay, total_runtime/1000/60),  size="xx-small", family='monospace')

    # Close figure
    plt.clf()

    print("FleetConfig makeup: {}\n Total seats: {}".format(fleet_makeup, total_seats))
    print(
        "Service rate: {:.2%} (Serviced: {} + Denied: {} = {}) \nPickup delay: {:.2f} / Ride delay: {:.2f} \nRuntime: {:.2f}s".format(
            serviced / total_requests, serviced, denied, total_requests, avg_pk_delay, avg_ride_delay,
            total_runtime / 1000))

    # pprint(df_status)

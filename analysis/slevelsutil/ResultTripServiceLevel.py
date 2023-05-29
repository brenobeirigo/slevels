from collections import defaultdict

import pandas as pd
import request_track_util as rtrack
from Filter import Filter


class ResultTripServiceLevel:

    def __init__(self, instance):
        self.instance = instance
        self.df_all = self.build_trip_data()
        self.df_all = self.add_deviation(self.df_all)
        self.filter = Filter(self.df_all, self.instance)
        self.df_rejected = self.df_all[self.filter.denied]
        self.df_serviced = self.df_all[self.filter.serviced]
        self.df_not_serviced = self.df_all[~self.filter.first]
        self.df_first = self.df_all[self.filter.serviced & self.filter.first]
        self.df_second = self.df_all[self.filter.serviced & self.filter.second]

    @property
    def df_summary(self):
        dict_methods = defaultdict(list)
        print(self.serviced_count)
        for method in self.instance.request_track_experiment_labels:
            dict_methods["Serviced"].append(self.serviced_count.get(method, 0))
            dict_methods["Rejected"].append(self.rejected_count.get(method, 0))
            dict_methods["First-tier"].append(self.first_count.get(method, 0))
            dict_methods["Second-tier"].append(self.second_count.get(method, 0))
            dict_methods["Total"].append(
                self.second_count.get(method, 0) + self.first_count.get(method, 0) + self.rejected_count.get(method, 0))
        df_summary = pd.DataFrame.from_dict(dict_methods)
        df_summary = df_summary[["First-tier", "Second-tier", "Serviced", "Rejected", "Total"]]
        df_summary.set_index(pd.Index(self.instance.request_track_experiment_labels), inplace=True)
        return df_summary

    @property
    def dict_methods_class(self):
        dict_status = {
            "Serviced": self.df_serviced,
            "Rejected": self.df_rejected,
            "First-tier": self.df_first,
            "Second-tier": self.df_second
        }

        dict_methods_class = rtrack.get_dict_method_status(
            self.instance.dict_sl_class,
            self.instance.request_track_experiment_labels,
            dict_status,
            self.instance.headers
        )
        return dict_methods_class

    @property
    def df_summary_class_status_method(self):

        df_summary_class = pd.DataFrame.from_dict(self.dict_methods_class)
        print(df_summary_class)
        index_cols = [self.instance.headers["class"], self.instance.headers["status"], self.instance.headers["method"]]
        df_summary_class.set_index(index_cols, inplace=True)
        df_summary_class.sort_index(inplace=True)
        return df_summary_class

    def get_value_counts_dict(self, column_labels, count_label):
        real_cols = [self.instance.headers[c] for c in column_labels]
        df = self.df_all.groupby(real_cols)[self.instance.headers[count_label]].value_counts()
        return dict(df)

    def get_rejected_value_counts_dict(self, column_labels, count_label):
        real_cols = [self.instance.headers[c] for c in column_labels]
        df = self.df_rejected.groupby(real_cols)[self.instance.headers[count_label]].value_counts()
        return dict(df)

    @property
    def sq_class_count(self):
        return dict(self.df_all[self.instance.headers["class"]].value_counts())

    @property
    def df_summary_class(self):
        dict_methods_class = self.dict_methods_class

        df_summary_class = pd.DataFrame.from_dict(dict_methods_class)
        print(df_summary_class)
        df_summary_class.set_index(
            [self.instance.headers["class"], self.instance.headers["status"], self.instance.headers["method"]],
            inplace=True)
        df_summary_class.sort_index(inplace=True)
        return df_summary_class

    @property
    def df_rejected_method_class(self):
        df_rejected_method_class = self.df_summary_class.reset_index()
        df_rejected_method_class = df_rejected_method_class[
            df_rejected_method_class[self.instance.headers["status"]] == "Rejected"]
        return df_rejected_method_class

    @property
    def df_summary_percentages(self):
        df_summary = self.df_summary.apply(lambda row: row / row["Total"], axis=1)
        return df_summary.style.format("{:.2%}")

    def add_category_segmentation_classes(self, df):
        category_segmentation = pd.api.types.CategoricalDtype(
            categories=["Business", "Standard", "Low-cost"],
            ordered=True
        )
        df[self.instance.headers["class"]] = df[self.instance.headers["class"]].astype(category_segmentation)

    @property
    def second_count(self):
        return dict(self.df_second[self.instance.headers["method"]].value_counts())

    @property
    def first_count(self):
        return dict(self.df_first[self.instance.headers["method"]].value_counts())

    @property
    def serviced_count(self):
        return dict(self.df_serviced[self.instance.headers["method"]].value_counts())

    @property
    def rejected_count(self):
        return dict(self.df_rejected[self.instance.headers["method"]].value_counts())

    @property
    def not_serviced_count(self):
        df_not_serviced_count = self.df_not_serviced.copy()

        columns = [
            self.instance.headers["class"],
            self.instance.headers["method"],
            self.instance.headers["service"],
        ]

        df_not_serviced_count = df_not_serviced_count[columns]
        df_not_serviced_count["count"] = 1
        df_not_serviced_count = df_not_serviced_count.groupby(
            [self.instance.headers["class"], self.instance.headers["method"],
             self.instance.headers["service"]]).sum().reset_index()

        return df_not_serviced_count

    @property
    def not_serviced_total_count(self):

        df_not_serviced_count = self.not_serviced_count.groupby(
            [self.instance.headers["class"], self.instance.headers["method"]]).sum().reset_index()

        return df_not_serviced_count

    @property
    def not_serviced_with_subtotal(self):
        df = self.not_serviced_count.copy()
        df2 = self.not_serviced_total_count.copy()

        from collections import defaultdict
        total_dict = defaultdict(list)
        for i, row in df2.iterrows():
            sq_class, policy, count = row
            total_dict[self.instance.headers["class"]].append(sq_class)
            total_dict[self.instance.headers["method"]].append(policy)
            total_dict[self.instance.headers["service"]].append("Total")
            total_dict["count"].append(count)

        total_data = pd.DataFrame(total_dict)
        df = df.append(total_data)
        columns = [self.instance.headers["class"], self.instance.headers["method"], self.instance.headers["service"]]
        df = df.sort_values(by=columns).set_index(columns).reset_index()
        return df

    def add_deviation(self, df):
        def get_diff_pk_delay_from_target(row):
            delay_pk = row[self.instance.headers["delay_pk"]]
            target_pk = self.instance.sl_class_target_pickup[row[self.instance.headers["class"]]]
            return max(0, delay_pk - target_pk)

        df = df.copy()
        df["deviation"] = df.apply(lambda row: get_diff_pk_delay_from_target(row), axis=1)
        return df

    def build_trip_data(self):
        request_track_experiment_labels, experiments = list(zip(*self.instance.label_method_path_data_dict.items()))
        df_methods_list = [rtrack.read_request_track(self.instance.instances_folder, experiment) for experiment in
                           experiments]
        df_methods = rtrack.concat_dfs(df_methods_list, request_track_experiment_labels)
        df_methods = rtrack.add_labels(df_methods, self.instance.dict_sl_class, self.instance.dict_sl_tier,
                                       self.instance.dict_fleet,
                                       self.instance.headers)

        return df_methods

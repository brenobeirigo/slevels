import df_util
from file_util import load_json


class Instance:

    def __init__(self, test_case, instance_settings_path, data_dict_path):
        self.test_case = test_case
        self.instance_settings_path = instance_settings_path
        self.instances_dic = load_json(instance_settings_path)

        # Folder where results will be saved
        self.result_folder = self.instances_dic["result_folder"]
        self.request_log_folder = self.result_folder + "request_track/"
        self.round_log_folder = self.result_folder + "round_track/"
        self.vehicle_status_graph_folder = self.result_folder + "vehicle_status/"

        # Folder where instances are located
        self.instances_folder = self.instances_dic["instances_folder"]

        # File name aggregated data
        self.instance_name = self.instances_dic["instance_name"]

        # Data dictionary for the request track file
        dict_request_track = load_json(data_dict_path)
        self.paper_folder = dict_request_track["paper_folder"]
        self.dict_sl_class = dict_request_track["dict_sl_class"]
        self.dict_fleet = dict_request_track["dict_fleet"]
        self.dict_sl_tier = dict_request_track["dict_sl_tier"]
        self.headers = dict_request_track["headers"]
        self.label_methods = dict_request_track["methods"]
        self.dict_exp_config = dict_request_track["test_cases"][test_case]
        self.request_track_experiment_labels, self.experiments = list(zip(*self.label_method_path_data_dict.items()))
        self.order_classes = dict_request_track["order_classes"]

        self.dict_contract_duration = dict_request_track["dict_contract_duration"]
        self.dict_segmentation = dict_request_track["dict_segmentation"]
        self.dict_method = dict_request_track["dict_method"]
        self.dict_service_rate = dict_request_track["dict_service_rate"]
        self.dict_maximal_hiring_delay = dict_request_track["dict_maximal_hiring_delay"]

        # Categories
        self.category_maximal_hiring_delay = df_util.get_categories(self.dict_maximal_hiring_delay.values())
        self.category_status = df_util.get_categories(self.dict_segmentation.values())
        self.category_segmentation = df_util.get_categories(self.dict_segmentation.values())
        self.category_contract_duration = df_util.get_categories(self.dict_contract_duration.values())
        self.category_method = df_util.get_categories(self.dict_method.values())
        self.category_service_rate = df_util.get_categories(self.dict_service_rate.values())

        # TODO matching vs method
        self.categories = {
            "maximal_hiring_delay": self.category_maximal_hiring_delay,
            "customer_segmentation": self.category_segmentation,
            "contract_duration": self.category_contract_duration,
            "method": self.category_method,
            "matching": self.category_method,
            "service_rate": self.category_service_rate,
        }

    def get_headers_from_tags(self, tag_list):
        return [self.headers[t] for t in tag_list]

    def apply_categories_columns(self, df_origin, columns):
        df = df_origin.copy()
        for h in columns:
            df[self.headers[h]] = df_util.set_categorical(df[self.headers[h]], self.categories[h])
        return df

    @property
    def order_methods_dict(self):
        order_classes = dict()
        for label_test_case, test_case_data_dict in self.dict_exp_config.items():
            order_classes[self.label_methods[label_test_case]] = test_case_data_dict["order"]
        return order_classes

    @property
    def label_method_path_data_dict(self):
        return {
            self.label_methods[label]: data["source"]
            for label, data in self.dict_exp_config.items()
        }

    @property
    def sl_class_target_pickup(self):
        sl_class_target_pickup = dict()
        for sl_class, target_pickup in self.instances_dic["scenario_config"]["service_level"].items():
            sl_class_target_pickup[self.dict_sl_class[sl_class]] = target_pickup["pk_delay_target"]
        return sl_class_target_pickup

    @staticmethod
    def get_instance_settings(label_setting_dic, file_name):
        """Read file name and return instance settings dictionary"""

        instance_settings = {}

        for setting in file_name.split("_"):

            try:
                config, value = setting.split('-')
                label = label_setting_dic[config]
                instance_settings[label] = value

            except Exception as e:
                # print(f"Cant split setting '{setting}' of file '{file_name}'. Exception: {e}")
                label = label_setting_dic[setting]
                instance_settings[label] = True

        return instance_settings

    def rename_headers(self, df):
        return df.rename(columns=self.headers)

    def rename_values(self, df):
        df_renamed = df.copy()
        if "customer_segmentation" in df_renamed.columns:
            df_renamed["customer_segmentation"].replace(self.dict_segmentation, inplace=True)

        if "matching" in df_renamed.columns:
            df_renamed["matching"].replace(self.dict_method, inplace=True)

        if "service_rate" in df_renamed.columns:
            df_renamed["service_rate"].replace(self.dict_service_rate, inplace=True)

        if "maximal_hiring_delay" in df_renamed.columns:
            df_renamed["maximal_hiring_delay"].replace(self.dict_maximal_hiring_delay, inplace=True)

        return df_renamed

    def set_index(self, df, index_labels):
        cols = [self.headers[i] for i in index_labels]
        return df.set_index(cols)

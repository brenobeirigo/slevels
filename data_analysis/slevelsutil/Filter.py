class Filter:

    def __init__(self, df, instance):
        self.df_all = df
        self.instance = instance

    @property
    def freelance(self):
        return self.df_all[self.instance.headers["service"]] == self.instance.dict_fleet["FREELANCE"]

    @property
    def denied(self):
        return self.df_all[self.instance.headers["service"]] == self.instance.dict_fleet["DENIED"]

    @property
    def fleet(self):
        return self.df_all[self.instance.headers["service"]] == self.instance.dict_fleet["FLEET"]

    @property
    def serviced(self):
        return self.fleet | self.freelance

    @property
    def first(self):
        return self.df_all[self.instance.headers["service_level"]] == self.instance.dict_sl_tier["FIRST"]

    @property
    def second(self):
        return self.df_all[self.instance.headers["service_level"]] == self.instance.dict_sl_tier["SECOND"]

    def sq_class(self, label):
        return self.df_all[self.instance.headers["class"]] == label

    def method(self, label):
        return self.df_all[self.instance.headers["method"]] == label


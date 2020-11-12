import pandas as pd


def format_as_time(df):
    return df.applymap(lambda e: ('{:02}:{:02}'.format(int(e) // 60, int(e) % 60) if e != 0 else '-'))


def format_as_percentage(df, zero='-', small="*", cases=2):
    formatter_cases = f'{{:.{cases}%}}'

    def format_cell(e):
        if e == 0:
            return zero
        if small is not None and e < 0.01:
            return small
        return formatter_cases.format(float(e))

    # Transforming to minutes
    return df.applymap(lambda e: format_cell(e))


def get_categories(categories):
    return pd.api.types.CategoricalDtype(categories=categories, ordered=True)


def set_categorical(df_col, categories):
    return df_col.astype(categories)


def set_to_int(df_col):
    return df_col.astype(int)


def set_to_str(df_col):
    return df_col.astype(str)


def set_columns_to_int(df, col_list):
    for col in col_list:
        df[col] = set_to_int(df[col])

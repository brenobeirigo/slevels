import json
import os


def load_json(path):
    """Read json file and return dictionary"""

    # Add .json to the end of file if needed
    if path.find(".json") < 0:
        path = path + ".json"

    # Read JSON file
    with open(path) as data_file:
        data_loaded = json.load(data_file)

    return data_loaded


def df_to_latex(df, column_format=None):
    return df.to_latex(multicolumn=True, multirow=True, column_format=column_format, sparsify=True)


def is_file(folder, filename):
    return os.path.isfile(os.path.join(folder, filename))


def read_files_from_folder(folder, filter_folder=True):

    file_names = os.listdir(folder)

    if filter_folder:
        file_names = [f for f in file_names if is_file(folder, f)]

    print(f"Reading {len(file_names)} files from '{folder}'.")

    return file_names

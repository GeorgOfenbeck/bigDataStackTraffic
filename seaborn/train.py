import seaborn as sns
import pandas as pd  # for data analysis
import numpy as np  # for scientific calculation
import seaborn as sns  # for statistical plotting
import datetime  # for working with date fields
import matplotlib.pyplot as plt  # for plotting
import math  # for mathematical calculation


nydata = pd.read_csv("./data/yellow_tripdata_2016-03.csv")
from math import radians, cos, sin, asin, sqrt


def distance(lon1, lat1, lon2, lat2):
    """
    Calculate the great circle distance between two points
    on the earth (specified in decimal degrees)
    """
    # convert decimal degrees to radians
    lon1, lat1, lon2, lat2 = map(radians, [lon1, lat1, lon2, lat2])

    # haversine formula
    dlon = lon2 - lon1
    dlat = lat2 - lat1
    a = sin(dlat / 2) ** 2 + cos(lat1) * cos(lat2) * sin(dlon / 2) ** 2
    c = 2 * asin(sqrt(a))
    r = 6371  # Radius of earth in kilometers. Use 3956 for miles
    return c * r


nydata["tpep_pickup_datetime"] = pd.to_datetime(nydata["tpep_pickup_datetime"])
nydata["tpep_dropoff_datetime"] = pd.to_datetime(nydata["tpep_dropoff_datetime"])


nydata["duration"] = (
    nydata["tpep_dropoff_datetime"] - nydata["tpep_pickup_datetime"]
).dt.total_seconds()
nydata["distance"] = nydata.apply(
    lambda x: distance(
        x["pickup_longitude"],
        x["pickup_latitude"],
        x["dropoff_longitude"],
        x["dropoff_latitude"],
    ),
    axis=1,
)

nydata["speed"] = nydata.distance / (nydata.duration / 3600)


nyc_taxi_final = nydata.drop(
    [
        "VendorID",
        "pickup_longitude",
        "pickup_latitude",
        "dropoff_longitude",
        "dropoff_latitude",
        "store_and_fwd_flag",
    ],
    axis=1,
)


nyc_taxi_final["pickup_min"] = nyc_taxi_final["tpep_pickup_datetime"].apply(
    lambda x: x.minute
)
nyc_taxi_final["pickup_hour"] = nyc_taxi_final["tpep_pickup_datetime"].apply(
    lambda x: x.hour
)
nyc_taxi_final["pickup_day"] = nyc_taxi_final["tpep_pickup_datetime"].apply(
    lambda x: x.day
)
nyc_taxi_final["pickup_month"] = nyc_taxi_final["tpep_pickup_datetime"].apply(
    lambda x: int(x.month)
)
nyc_taxi_final["pickup_weekday"] = nyc_taxi_final["tpep_pickup_datetime"].dt.day_name()
nyc_taxi_final["pickup_month_name"] = nyc_taxi_final[
    "tpep_pickup_datetime"
].dt.month_name()

nyc_taxi_final["drop_hour"] = nyc_taxi_final["tpep_dropoff_datetime"].apply(
    lambda x: x.hour
)
nyc_taxi_final["drop_month"] = nyc_taxi_final["tpep_dropoff_datetime"].apply(
    lambda x: int(x.month)
)
nyc_taxi_final["drop_day"] = nyc_taxi_final["tpep_dropoff_datetime"].apply(
    lambda x: x.day
)
nyc_taxi_final["drop_min"] = nyc_taxi_final["tpep_dropoff_datetime"].apply(
    lambda x: x.minute
)

df = nyc_taxi_final[(nyc_taxi_final["speed"] < 1) & (nyc_taxi_final["distance"] == 0)]
nyc_taxi_final.drop(df.index, inplace=True)

df = nyc_taxi_final[
    (nyc_taxi_final["pickup_day"] < nyc_taxi_final["drop_day"])
    & (nyc_taxi_final["duration"] > 10000)
    & (nyc_taxi_final["distance"] < 5)
    & (nyc_taxi_final["pickup_hour"] < 23)
]
nyc_taxi_final.drop(df.index, inplace=True)

df = nyc_taxi_final[(nyc_taxi_final["speed"] < 1) & (nyc_taxi_final["distance"] < 1)]
nyc_taxi_final.drop(df.index, inplace=True)

nyc_taxi_final[nyc_taxi_final["duration"] / 60 > 10000][["duration", "distance"]]
nyc_taxi_final[nyc_taxi_final["duration"] / 60 > 10000]["duration"]

df = nyc_taxi_final[nyc_taxi_final["distance"] < 0.2]
nyc_taxi_final.drop(df.index, inplace=True)

df = nyc_taxi_final[nyc_taxi_final["passenger_count"] == 0]
nyc_taxi_final.drop(df.index, inplace=True)

df = nyc_taxi_final[nyc_taxi_final["duration"] < 120]
nyc_taxi_final.drop(df.index, inplace=True)

df = nyc_taxi_final[nyc_taxi_final["speed"] > 50]["speed"]
nyc_taxi_final.drop(df.index, inplace=True)


# Import Sklearn and models
from sklearn import linear_model
from sklearn.model_selection import train_test_split
from sklearn import metrics
from sklearn import preprocessing
from sklearn.model_selection import cross_val_score, KFold

nyc_taxi_final_sampling = nyc_taxi_final.sample(n=500000, replace="False")

# Applying Standard Scaler

X2 = nyc_taxi_final_sampling.drop(
    [
        "tpep_pickup_datetime",
        "tpep_dropoff_datetime",
        "trip_distance",
        "fare_amount",
        "extra",
        "mta_tax",
        "tip_amount",
        "tolls_amount",
        "improvement_surcharge",
        "total_amount",
        "duration",
        "speed",
        "pickup_weekday",
        "pickup_month_name",
    ],
    axis=1,
)

X2["distance"] = X2["distance"].apply(lambda x: int(x))
X1 = preprocessing.scale(X2)
X = pd.DataFrame(X1)
y = nyc_taxi_final_sampling["duration"]


X_train, X_test, y_train, y_test = train_test_split(
    X, y, test_size=0.3, random_state=111
)
reg = linear_model.LinearRegression()

reg.fit(X_train, y_train)
print("reg.intercept_=> %10.10f" % (reg.intercept_))
# print(list(zip(feature_columns, reg.coef_)))
y_pred = reg.predict(X_test)
rmse_val = np.sqrt(metrics.mean_squared_error(y_test, y_pred))

# Null RMSE
y_null = np.zeros_like(y_test, dtype=int)
y_null.fill(y_test.mean())
N_RMSE = np.sqrt(metrics.mean_squared_error(y_test, y_null))
# Metrics
print("Mean Absolute Error    :", metrics.mean_absolute_error(y_test, y_pred))
print("Mean Squared Error     :", metrics.mean_squared_error(y_test, y_pred))
print("Root Mean Squared Error = ", rmse_val)
print("Null RMSE = ", N_RMSE)
if N_RMSE < rmse_val:
    print("Model is Not Doing Well Null RMSE Should be Greater")
else:
    print("Model is Doing Well Null RMSE is Greater than RMSE")
# Train RMSE
y_pred_test = reg.predict(X_train)
rmse_val = np.sqrt(metrics.mean_squared_error(y_train, y_pred_test))
print(
    "Train Root Mean Squared Error:",
    np.sqrt(metrics.mean_squared_error(y_train, y_pred_test)),
)
# Error Percentage
df = pd.DataFrame({"Actual": y_test, "Predicted": y_pred, "Error": y_test - y_pred})
print("Maximum Error is :", df.Error.max())
print("Minimum Error is :", df.Error.min())
# Score
scores = cross_val_score(reg, X_train, y_train, cv=5)
print("Mean cross-validation score: %.2f" % scores.mean())

import joblib
joblib.dump(reg, "./nyc_taxi_model.pkl")
from skyfield.api import load, EarthSatellite, wgs84, utc
from skyfield.timelib import Time
from skyfield.toposlib import GeographicPosition
from skyfield.vectorlib import VectorSum
from skyfield.positionlib import Barycentric
from skyfield.data import mpc
from tle2czml.tle2czml import tles_to_czml
import numpy as np


from datetime import datetime, timedelta
from typing import Callable, cast
import json
import os


ts = load.timescale()
# 加载天文常数并创建观测场景
eph = load("./src/assets/de421.bsp")
earth = eph["earth"]
sun = eph["Sun"]
moon = eph["Moon"]


class toTopsCaculator:
    """_summary_"""

    caculate_time_start: Time = ts.now()
    """计算开始时间
    """
    caculate_time_end: Time = ts.utc(
        caculate_time_start.utc_datetime() + timedelta(days=50)
    )
    """计算结束时间
    """
    czmlTimeOffset = 0
    """czml计算时间偏移
    """
    altitude_degrees = 0
    """过境最小天顶角
    """
    target_czml_out_path = ""
    """要计算的czml文件输出路径
    """
    tles_dir = ""
    """默认tle存放的文件夹
    """
    czml_out_dir = ""
    """默认tle计算的czml存放的文件夹
    """
    toTopRes_out_dir = ""
    """过境结果存放文件夹
    """

    def __init__(
        self,
        czmlTimeOffset: float,
        caculate_time_start: str,
        caculate_time_end: str,
        altitude_degrees: float,
        target_czml_out_path: str,
        tles_dir: str,
        czml_out_dir: str,
        toTopRes_out_dir: str,
    ):
        """构造函数

        Args:
            czmlTimeOffset (float): czml计算时间偏移
            caculate_time_start (Time): czml计算开始时间 格式：2025-05-31 00:00:00
            caculate_time_end (Time): czml计算结束时间
            altitude_degrees (float): 过境最小天顶角
            target_czml_out_path (str): 要计算的czml文件输出路径
            tles_dir (str): 默认tle存放的文件夹
            czml_out_dir (str): 默认tle计算的czml存放的文件夹
            toTopRes_out_dir (str): 过境结果存放文件夹
        """

        self.czmlTimeOffset = czmlTimeOffset
        self.altitude_degrees = altitude_degrees
        self.target_czml_out_path = target_czml_out_path
        self.tles_dir = tles_dir
        self.czml_out_dir = czml_out_dir
        self.toTopRes_out_dir = toTopRes_out_dir
        self.caculate_time_start = ts.from_datetime(
            datetime.strptime(caculate_time_start, "%Y-%m-%d %H:%M:%S").replace(
                tzinfo=utc
            )
        )
        self.caculate_time_end = ts.from_datetime(
            datetime.strptime(caculate_time_end, "%Y-%m-%d %H:%M:%S").replace(
                tzinfo=utc
            )
        )

    def setTargetCzml(
        self,
        LLA: list[float],
        resJson,
        file_content: str,
        callback: Callable | None = None,
    ):
        """通过前端传入的tle计算过境时间以及对应的czml

        Args:
            LLA (list[float]): 经纬高数组
            resJson (_type_): 输出的fontendConfig.json的内容
            file_content (_type_): tle文件的内容
            callback (_type_, optional): 回调函数. Defaults to None.
        """

        # 读取tle
        tle = file_content.splitlines()
        lines = [line for line in tle]
        for i in range(0, len(lines), 3):
            name = lines[i].split("\n")[0]
            line1 = lines[i + 1].split("\n")[0]
            line2 = lines[i + 2].split("\n")[0]
            # 初始化卫星
            satellite = EarthSatellite(line1, line2, name, ts)
            # 设置观察点
            observer = wgs84.latlon(
                longitude_degrees=LLA[0],
                latitude_degrees=LLA[1],
                elevation_m=LLA[2],
            )
            # 查找过境事件
            times, events = satellite.find_events(
                observer,
                self.caculate_time_start,
                self.caculate_time_end,
                altitude_degrees=self.altitude_degrees,
            )
            self.getEventsFromResult(events, times, satellite, observer)
            eventsMap = [event for event in list(zip(times, events)) if event[1] == 1]

            if len(eventsMap) > 0:
                try:
                    # 写入czml结果数据
                    czml_data = tles_to_czml(
                        file_content,
                        start_time=datetime.fromisoformat(eventsMap[0][0].utc_iso())
                        - timedelta(seconds=self.czmlTimeOffset),
                    )
                    with open(self.target_czml_out_path, "w") as f:
                        f.write(czml_data)

                    # 写入resJson
                    resJson["czml"].append(
                        {
                            "tle_file": "",
                            "czmlFile": f"/output/outResult.czml",
                        }
                    )

                    # 回调函数
                    if callback is not None:
                        callback(
                            resJson,
                            eventsMap[0][0].utc_iso(),
                        )

                except Exception as e:
                    print(e)

    def setDefaultCzml(self, resJson, time):
        """通过时间计算tles文件夹下tle的czml

        Args:
            resJson (_type_): _description_
            time (_type_): _description_
        """

        for filename in os.listdir(self.tles_dir):
            if filename.lower().endswith(".tle"):
                # 读取tle文件内容
                tle_path = os.path.join(self.tles_dir, filename)
                with open(tle_path, "r") as tle_file:
                    tle = tle_file.read()

                # 设置czml文件输出路径
                output_filename = os.path.splitext(filename)[0] + ".czml"
                czml_output_file = os.path.join(self.czml_out_dir, output_filename)

                # 写入resJson内容
                resJson["default"].append(
                    {
                        "name": os.path.splitext(filename)[0],
                        "load": False,
                        "showLabel": False,
                        "path": f"/output/{output_filename}",
                    }
                )

                # 写入czml内容
                with open(czml_output_file, "w") as f:
                    f.write(
                        tles_to_czml(
                            tle,
                            start_time=datetime.fromisoformat(time)
                            - timedelta(seconds=self.czmlTimeOffset),
                        )
                    )

    def getEventsFromResult(
        self, events, times, satellite: EarthSatellite, observer: GeographicPosition
    ):
        all_passes = []
        """过境结果
        """

        # 计算过境结果
        for i in range(0, len(events) - 2, 3):
            if events[i] != 0 or events[i + 2] != 2:
                continue
            pass_data = []
            t_start = times[i].utc_datetime()
            t_end = times[i + 2].utc_datetime()
            current = t_start
            while current <= t_end:
                t_sample = ts.utc(current)
                difference = satellite - observer

                observer_position = observer.at(t_sample)
                satellite_position = satellite.at(t_sample)
                sun_position = earth.at(t_sample).observe(sun)
                moon_position = earth.at(t_sample).observe(moon)

                # 计算位置向量
                obs_vec = np.array(observer_position.position.km)
                sat_vec = np.array(satellite_position.position.km)
                sun_vec = np.array(sun_position.position.km)
                moon_vec = np.array(moon_position.position.km)

                # 计算方向向量
                sat_obs_vec = np.subtract(sat_vec, obs_vec)
                sun_obs_vec = np.subtract(sun_vec, obs_vec)
                moon_obs_vec = np.subtract(moon_vec, obs_vec)

                # 计算太阳夹角
                cos_sat_obs_sun = np.dot(sat_obs_vec, sun_obs_vec) / (
                    np.linalg.norm(sat_vec) * np.linalg.norm(sun_vec)
                )
                angle_sat_sun = np.arccos(cos_sat_obs_sun) * 180 / np.pi
                # 计算月亮夹角
                cos_sat_obs_moon = np.dot(sat_obs_vec, moon_obs_vec) / (
                    np.linalg.norm(sat_vec) * np.linalg.norm(moon_vec)
                )
                angle_sat_moon = np.arccos(cos_sat_obs_moon) * 180 / np.pi

                topocentric = difference.at(t_sample)
                alt, az, distance = topocentric.altaz()
                geoPosition = satellite.at(t_sample)
                lat, lon = wgs84.latlon_of(geoPosition)
                h = wgs84.height_of(geoPosition)
                # 计算 2 秒后仰角判断升降状态
                alt_after = cast(
                    float,
                    (satellite - observer)
                    .at(ts.utc(current + timedelta(seconds=2)))
                    .altaz()[0]
                    .degrees,
                )
                alt_now = cast(float, alt.degrees)
                if (alt_after is not None) & (alt_now is not None):
                    status = "升轨" if alt_after > alt_now else "降轨"
                    pass_data.append(
                        {
                            "time": current.isoformat(),
                            "elevation": round(alt_now, 2),
                            "distance_km": round(cast(float, distance.km), 2),
                            "status": status,
                            "sunAngle": angle_sat_sun,
                            "moonAngle": angle_sat_moon,
                            "LLA": [lon.degrees, lat.degrees, h.km],
                        }
                    )
                current += timedelta(seconds=2)
            all_passes.append(pass_data)

        # 写入过境结果
        try:
            with open(self.toTopRes_out_dir + "/res.json", "w", encoding="utf-8") as fw:
                json.dump(all_passes, fw, ensure_ascii=False, indent=2)
        except FileNotFoundError as e:
            print(e)

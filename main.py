from flask import Flask, jsonify, send_from_directory, abort, request
import json
import os
from src.modules.caculateTotops import toTopsCaculator
import sys

# 设置当前工作目录为 exe 所在目录（打包为exe时放开下面的注释）
os.chdir(os.path.dirname(sys.executable))
out_base_dir = "./src/assets/"
tles_dir = out_base_dir + "tles"
czml_out_dir = out_base_dir + "output/czmlOut"
target_czml_out_path = os.path.join(czml_out_dir, "outResult.czml")
toTopRes_out_dir = out_base_dir + "output/toTopOut"
jsonConfig_out_path = out_base_dir + "fontendConfig.json"
os.makedirs(czml_out_dir, exist_ok=True)

czmlTimeOffset_default = 20
altitude_degrees_default = 88
caculate_start_time_default = "2025-05-31 00:00:00"
caculate_end_time_default = "2025-06-02 00:00:00"
LLA_default = [120, 40, 0]

app = Flask(__name__)


@app.route("/history", methods=["GET"])
def history():
    try:
        with open(jsonConfig_out_path, "r", encoding="utf-8") as f:
            data = json.load(f)
        return jsonify(
            {
                "data": data,
                "status": "success",
            }
        )
    except FileNotFoundError:
        abort(404)


@app.route("/convert", methods=["POST"])
def convert():
    # 解析文件
    if "tle_file" not in request.files:
        return jsonify({"error": "No file part"}), 400
    file = request.files["tle_file"]
    if file.filename == "":
        return jsonify({"error": "No selected file"}), 400

    # 接收前端传来的参数
    convert_options = request.form.to_dict().get("convert_options")
    if convert_options is None:
        return jsonify({"error": "Missing convert_options"}), 400
    params = json.loads(convert_options)
    czmlTimeOffset = params.get("timeOffset", czmlTimeOffset_default)
    altitude_degrees = params.get("altitudeDegrees", altitude_degrees_default)
    caculate_start_time = params.get("startTime", caculate_start_time_default)
    caculate_end_time = params.get("endTime", caculate_end_time_default)
    LLA = params.get("LLA", LLA_default)
    try:
        resJson = {"default": [], "czml": []}
        file_content = file.read().decode("utf-8")
        toTopsCaculatorNew = toTopsCaculator(
            czmlTimeOffset,
            caculate_start_time,
            caculate_end_time,
            altitude_degrees,
            target_czml_out_path,
            tles_dir,
            czml_out_dir,
            toTopRes_out_dir,
        )
        toTopsCaculatorNew.setTargetCzml(
            LLA,
            resJson,
            file_content,
            # toTopsCaculatorNew.setDefaultCzml,
        )

        try:
            with open(jsonConfig_out_path, "w", encoding="utf-8") as fw:
                json.dump(resJson, fw, ensure_ascii=False, indent=2)
                return jsonify(
                    {
                        "status": "success",
                        "data": {"code": 200, "path": target_czml_out_path},
                    }
                )
        except FileNotFoundError:
            abort(404)
    except Exception as e:
        print(e)
        return jsonify({"error": f"Failed to read file: {str(e)}"}), 400

    return jsonify({"status": "ok"})


# 代理 tles 目录下的文件
@app.route("/tles/<path:filename>", methods=["GET"])
def proxy_tles(filename):
    tles_dir = os.path.abspath("./tles")
    if not os.path.isfile(os.path.join(tles_dir, filename)):
        abort(404)
    return send_from_directory(tles_dir, filename)


# 代理 output 目录下的文件
@app.route("/output/<path:filename>", methods=["GET"])
def proxy_output(filename):
    output_dir = os.path.abspath("./output")
    if not os.path.isfile(os.path.join(output_dir, filename)):
        abort(404)
    return send_from_directory(output_dir, filename)


if __name__ == "__main__":
    app.run(host="127.0.0.1", port=5000, debug=True)

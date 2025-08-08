tel转czml

1. 静态资源文件夹 `assets`; 文件输出文件夹 `assets/output`（`czmlOut`：输出的czml文件； `toTopOut`：输出卫星过境信息的文件）;

2. 主要启动脚本 `main.py` 。启动命令

    ```shell
    .venv\Scripts\activate
    ```

    ```shell
     python main.py
    ```

    或者

    ```shell
     uv run main.py
    ```

3. 模块文件夹 `src\modules`

4. 项目使用`python3.12.10`，虚拟环境在`.venv`文件夹下，项目使用`uv`管理，同步依赖在根目录下运行

    ```shell
    uv sync
    ```

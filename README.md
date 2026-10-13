# cad-parser

Java 17 + Spring Boot 服务：解析 **ASCII DXF**，并把几何实体输出成 JSON。  
**DWG** 是 Autodesk 专有格式，本工程通过外部转换器（ODA File Converter 或 LibreDWG `dwg2dxf`）转成 DXF 后再解析。

## 怎么跑

需要本机已安装 JDK 17+ 和 Maven。

```bash
cd D:\cad-parser
mvn test
mvn spring-boot:run
```

服务默认监听 `http://localhost:8080`。

解析文件：

```bash
curl -F "file=@src/test/resources/fixtures/sample.dxf" http://localhost:8080/api/cad/parse
```

查看 DWG 转换器是否可用：

```bash
curl http://localhost:8080/api/cad/health
```

## 架构

```
上传 DXF/DWG
    -> CadController
    -> CadParseService
         -> CadFormatDetector          魔数 / 扩展名
         -> DwgToDxfConverter          仅 DWG：外部 CLI 转 DXF
         -> DxfParser                  ASCII DXF group-code 解析
    -> CadParseResponse JSON
```

| 职责 | 文件 |
| --- | --- |
| 入口 | `src/main/java/com/cadparser/CadParserApplication.java` |
| HTTP | `src/main/java/com/cadparser/api/CadController.java` |
| 编排 | `src/main/java/com/cadparser/service/CadParseService.java` |
| DXF 解析 | `src/main/java/com/cadparser/parse/dxf/DxfParser.java` |
| DWG 转换 | `src/main/java/com/cadparser/convert/DwgToDxfConverter.java` |
| 配置 | `src/main/resources/application.yml` |
| 示例图纸 | `src/test/resources/fixtures/sample.dxf` |

已支持的 DXF 实体：`LINE`、`POINT`、`CIRCLE`、`ARC`、`LWPOLYLINE`、`POLYLINE`、`TEXT`/`MTEXT`、`INSERT`、`ELLIPSE`、`SPLINE`、`SOLID`/`3DFACE`。未专门建模的类型仍会带上图层和 `10/20/30` 点。

## DWG

1. 安装 [ODA File Converter](https://www.opendesign.com/guestfiles/oda_file_converter)（推荐，Windows），或安装 LibreDWG 的 `dwg2dxf`。
2. 在 `application.yml` 里可指定路径：

```yaml
cad:
  dwg:
    converter: auto          # auto | oda | dwg2dxf | none
    oda-converter-path: ""   # 空则扫描 Program Files\ODA
    dwg2dxf-path: dwg2dxf
```

没有转换器时，DWG 上传会返回 422，并提示导出为 ASCII DXF。

## 适合下手改的第一处

在 `DxfParser.toEntity(...)` 里为一个新实体类型增加分支。  
例如支持 `HATCH` 或 `DIMENSION`：读 group code、填 `CadEntity.properties` 和 `points`，然后在 `sample.dxf` 加一段对应实体、在 `DxfParserTest` 断言即可。

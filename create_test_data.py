#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import json
import urllib.request
import urllib.parse
from typing import Dict, Optional

BASE_URL = "http://localhost:8080/api/v1"

# 存储创建的ID
ids: Dict[str, str] = {}

def create_instance(object_type: str, data: dict, key: Optional[str] = None) -> Optional[str]:
    """创建实例"""
    print(f"创建 {object_type}...")
    try:
        # URL 编码对象类型名称
        encoded_type = urllib.parse.quote(object_type, safe='')
        url = f"{BASE_URL}/instances/{encoded_type}"
        json_data = json.dumps(data, ensure_ascii=False).encode('utf-8')
        
        req = urllib.request.Request(
            url,
            data=json_data,
            headers={"Content-Type": "application/json; charset=utf-8"},
            method="POST"
        )
        
        with urllib.request.urlopen(req) as response:
            result = json.loads(response.read().decode('utf-8'))
            instance_id = result.get("data", {}).get("id")
            if instance_id:
                print(f"✓ 创建成功: {instance_id}")
                if key:
                    ids[key] = instance_id
                return instance_id
            else:
                print(f"✗ 创建失败: {result}")
                return None
    except Exception as e:
        print(f"✗ 创建失败: {e}")
        return None

def create_link(link_type: str, source_key: str, target_key: str, properties: Optional[dict] = None) -> bool:
    """创建关系"""
    source_id = ids.get(source_key)
    target_id = ids.get(target_key)
    
    if not source_id or not target_id:
        print(f"✗ 关系创建失败: 缺少源或目标ID ({source_key} -> {target_key})")
        return False
    
    print(f"创建关系 {link_type}: {source_key} -> {target_key}...")
    try:
        data = {
            "source_id": source_id,
            "target_id": target_id
        }
        if properties:
            data["properties"] = properties
        
        # URL 编码关系类型名称
        encoded_link_type = urllib.parse.quote(link_type, safe='')
        url = f"{BASE_URL}/links/{encoded_link_type}"
        json_data = json.dumps(data, ensure_ascii=False).encode('utf-8')
        
        req = urllib.request.Request(
            url,
            data=json_data,
            headers={"Content-Type": "application/json; charset=utf-8"},
            method="POST"
        )
        
        with urllib.request.urlopen(req) as response:
            print("✓ 关系创建成功")
            return True
    except Exception as e:
        print(f"✗ 关系创建失败: {e}")
        return False

def main():
    print("=" * 50)
    print("创建省中心联网收费业务测试数据")
    print("=" * 50)
    print()
    
    # 步骤 1: 创建路段业主
    print("步骤 1: 创建路段业主")
    create_instance("路段业主", {
        "业主名称": "江苏交通控股有限公司",
        "统一社会信用代码": "91320000123456789X"
    }, "路段业主_江苏交通控股有限公司")
    
    create_instance("路段业主", {
        "业主名称": "浙江交通投资集团",
        "统一社会信用代码": "91330000987654321Y"
    }, "路段业主_浙江交通投资集团")
    print()
    
    # 步骤 2: 创建收费公路
    print("步骤 2: 创建收费公路")
    create_instance("收费公路", {
        "公路名称": "沪宁高速公路",
        "公路编码": "G42",
        "起始桩号": "K0+000",
        "终止桩号": "K274+000"
    }, "收费公路_沪宁高速公路")
    
    create_instance("收费公路", {
        "公路名称": "沪杭高速公路",
        "公路编码": "G60",
        "起始桩号": "K0+000",
        "终止桩号": "K151+000"
    }, "收费公路_沪杭高速公路")
    print()
    
    # 步骤 3: 创建收费站
    print("步骤 3: 创建收费站")
    create_instance("收费站", {
        "收费站名称": "南京收费站",
        "收费站编码": "NJ001",
        "桩号": "K0+500"
    }, "收费站_南京收费站")
    
    create_instance("收费站", {
        "收费站名称": "上海收费站",
        "收费站编码": "SH001",
        "桩号": "K274+000"
    }, "收费站_上海收费站")
    
    create_instance("收费站", {
        "收费站名称": "杭州收费站",
        "收费站编码": "HZ001",
        "桩号": "K151+000"
    }, "收费站_杭州收费站")
    print()
    
    # 步骤 4: 创建ETC门架
    print("步骤 4: 创建ETC门架")
    create_instance("ETC门架", {
        "门架名称": "沪宁高速K50门架",
        "门架编码": "G42-K50",
        "桩号": "K50+000"
    }, "ETC门架_沪宁高速K50门架")
    
    create_instance("ETC门架", {
        "门架名称": "沪宁高速K150门架",
        "门架编码": "G42-K150",
        "桩号": "K150+000"
    }, "ETC门架_沪宁高速K150门架")
    
    create_instance("ETC门架", {
        "门架名称": "沪杭高速K75门架",
        "门架编码": "G60-K75",
        "桩号": "K75+000"
    }, "ETC门架_沪杭高速K75门架")
    print()
    
    # 步骤 5: 创建收费单元
    print("步骤 5: 创建收费单元")
    create_instance("收费单元", {
        "单元名称": "南京-镇江段",
        "单元编码": "NJ-ZJ",
        "里程": 65.5
    }, "收费单元_南京-镇江段")
    
    create_instance("收费单元", {
        "单元名称": "镇江-常州段",
        "单元编码": "ZJ-CZ",
        "里程": 68.2
    }, "收费单元_镇江-常州段")
    
    create_instance("收费单元", {
        "单元名称": "上海-嘉兴段",
        "单元编码": "SH-JX",
        "里程": 75.8
    }, "收费单元_上海-嘉兴段")
    print()
    
    # 步骤 6: 创建车道
    print("步骤 6: 创建车道")
    create_instance("车道", {
        "车道编号": "NJ001-01",
        "车道类型": "ETC"
    }, "车道_NJ001-01")
    
    create_instance("车道", {
        "车道编号": "NJ001-02",
        "车道类型": "MTC"
    }, "车道_NJ001-02")
    
    create_instance("车道", {
        "车道编号": "SH001-01",
        "车道类型": "ETC"
    }, "车道_SH001-01")
    print()
    
    # 步骤 7: 创建标识点
    print("步骤 7: 创建标识点")
    create_instance("标识点", {
        "标识点编码": "G42-K50-001",
        "标识点类型": "ETC门架",
        "桩号": "K50+000"
    }, "标识点_G42-K50-001")
    
    create_instance("标识点", {
        "标识点编码": "G42-K150-001",
        "标识点类型": "ETC门架",
        "桩号": "K150+000"
    }, "标识点_G42-K150-001")
    
    create_instance("标识点", {
        "标识点编码": "NJ001-01-001",
        "标识点类型": "车道",
        "桩号": "K0+500"
    }, "标识点_NJ001-01-001")
    print()
    
    # 步骤 8: 创建车辆
    print("步骤 8: 创建车辆")
    create_instance("车辆", {
        "车牌号": "苏A12345",
        "车辆类型": "小型客车",
        "车主姓名": "张三"
    }, "车辆_苏A12345")
    
    create_instance("车辆", {
        "车牌号": "沪B67890",
        "车辆类型": "小型客车",
        "车主姓名": "李四"
    }, "车辆_沪B67890")
    print()
    
    # 步骤 9: 创建通行介质
    print("步骤 9: 创建通行介质")
    create_instance("通行介质", {
        "介质编号": "ETC001234567890",
        "介质类型": "ETC卡",
        "发行日期": "2023-01-15"
    }, "通行介质_ETC001234567890")
    
    create_instance("通行介质", {
        "介质编号": "OBU987654321098",
        "介质类型": "OBU",
        "发行日期": "2023-03-20"
    }, "通行介质_OBU987654321098")
    print()
    
    # 步骤 10: 创建交易流水
    print("步骤 10: 创建交易流水")
    create_instance("交易流水", {
        "交易时间": "2024-12-19T10:30:00Z",
        "交易金额": 25.50,
        "交易类型": "ETC扣费"
    }, "交易流水_1")
    
    create_instance("交易流水", {
        "交易时间": "2024-12-19T10:35:00Z",
        "交易金额": 18.20,
        "交易类型": "ETC扣费"
    }, "交易流水_2")
    
    create_instance("交易流水", {
        "交易时间": "2024-12-19T11:00:00Z",
        "交易金额": 32.80,
        "交易类型": "MTC收费"
    }, "交易流水_3")
    print()
    
    # 步骤 11: 创建车辆通行路径
    print("步骤 11: 创建车辆通行路径")
    create_instance("车辆通行路径", {
        "通行开始时间": "2024-12-19T10:00:00Z",
        "通行结束时间": "2024-12-19T11:30:00Z",
        "通行状态": "已完成"
    }, "车辆通行路径_1")
    
    create_instance("车辆通行路径", {
        "通行开始时间": "2024-12-19T14:00:00Z",
        "通行结束时间": "2024-12-19T15:20:00Z",
        "通行状态": "已完成"
    }, "车辆通行路径_2")
    print()
    
    # 步骤 12: 创建通行拟合路径
    print("步骤 12: 创建通行拟合路径")
    create_instance("通行拟合路径", {
        "拟合时间": "2024-12-19T11:35:00Z",
        "拟合状态": "成功",
        "总里程": 274.0
    }, "通行拟合路径_1")
    
    create_instance("通行拟合路径", {
        "拟合时间": "2024-12-19T15:25:00Z",
        "拟合状态": "成功",
        "总里程": 151.0
    }, "通行拟合路径_2")
    print()
    
    # 步骤 13: 创建拆分明细
    print("步骤 13: 创建拆分明细")
    create_instance("拆分明细", {
        "拆分金额": 12.75,
        "拆分比例": 0.5,
        "拆分时间": "2024-12-19T11:40:00Z"
    }, "拆分明细_1")
    
    create_instance("拆分明细", {
        "拆分金额": 12.75,
        "拆分比例": 0.5,
        "拆分时间": "2024-12-19T11:40:00Z"
    }, "拆分明细_2")
    
    create_instance("拆分明细", {
        "拆分金额": 9.10,
        "拆分比例": 0.5,
        "拆分时间": "2024-12-19T11:40:00Z"
    }, "拆分明细_3")
    print()
    
    # 创建关系
    print("=" * 50)
    print("创建关系")
    print("=" * 50)
    print()
    
    print("创建管理关系")
    create_link("管理", "路段业主_江苏交通控股有限公司", "收费公路_沪宁高速公路")
    create_link("管理", "路段业主_浙江交通投资集团", "收费公路_沪杭高速公路")
    print()
    
    print("创建包含关系")
    create_link("包含收费站", "收费公路_沪宁高速公路", "收费站_南京收费站")
    create_link("包含收费站", "收费公路_沪宁高速公路", "收费站_上海收费站")
    create_link("包含收费站", "收费公路_沪杭高速公路", "收费站_杭州收费站")
    
    create_link("包含ETC门架", "收费公路_沪宁高速公路", "ETC门架_沪宁高速K50门架")
    create_link("包含ETC门架", "收费公路_沪宁高速公路", "ETC门架_沪宁高速K150门架")
    create_link("包含ETC门架", "收费公路_沪杭高速公路", "ETC门架_沪杭高速K75门架")
    
    create_link("包含收费单元", "收费公路_沪宁高速公路", "收费单元_南京-镇江段")
    create_link("包含收费单元", "收费公路_沪宁高速公路", "收费单元_镇江-常州段")
    create_link("包含收费单元", "收费公路_沪杭高速公路", "收费单元_上海-嘉兴段")
    
    create_link("包含车道", "收费站_南京收费站", "车道_NJ001-01")
    create_link("包含车道", "收费站_南京收费站", "车道_NJ001-02")
    create_link("包含车道", "收费站_上海收费站", "车道_SH001-01")
    print()
    
    print("创建代收关系")
    create_link("代收", "ETC门架_沪宁高速K50门架", "收费单元_南京-镇江段")
    create_link("代收", "ETC门架_沪宁高速K150门架", "收费单元_镇江-常州段")
    print()
    
    print("创建继承关系")
    create_link("继承标识点_ETC门架", "ETC门架_沪宁高速K50门架", "标识点_G42-K50-001")
    create_link("继承标识点_ETC门架", "ETC门架_沪宁高速K150门架", "标识点_G42-K150-001")
    create_link("继承标识点_车道", "车道_NJ001-01", "标识点_NJ001-01-001")
    print()
    
    print("创建持有关系")
    create_link("持有", "车辆_苏A12345", "通行介质_ETC001234567890", {
        "绑定时间": "2023-01-20T10:00:00Z",
        "绑定状态": "有效"
    })
    create_link("持有", "车辆_沪B67890", "通行介质_OBU987654321098", {
        "绑定时间": "2023-03-25T14:00:00Z",
        "绑定状态": "有效"
    })
    print()
    
    print("创建生成关系")
    create_link("生成", "标识点_G42-K50-001", "交易流水_1", {
        "生成时间": "2024-12-19T10:30:00Z"
    })
    create_link("生成", "标识点_G42-K150-001", "交易流水_2", {
        "生成时间": "2024-12-19T10:35:00Z"
    })
    create_link("生成", "标识点_NJ001-01-001", "交易流水_3", {
        "生成时间": "2024-12-19T11:00:00Z"
    })
    print()
    
    print("创建关联交易关系")
    create_link("关联交易", "车辆_苏A12345", "交易流水_1")
    create_link("关联交易", "车辆_苏A12345", "交易流水_2")
    create_link("关联交易", "车辆_沪B67890", "交易流水_3")
    print()
    
    print("创建汇聚为关系")
    create_link("汇聚为", "交易流水_1", "车辆通行路径_1")
    create_link("汇聚为", "交易流水_2", "车辆通行路径_1")
    create_link("汇聚为", "交易流水_3", "车辆通行路径_2")
    print()
    
    print("创建拟合为关系")
    create_link("拟合为", "车辆通行路径_1", "通行拟合路径_1", {
        "拟合时间": "2024-12-19T11:35:00Z"
    })
    create_link("拟合为", "车辆通行路径_2", "通行拟合路径_2", {
        "拟合时间": "2024-12-19T15:25:00Z"
    })
    print()
    
    print("创建拆分为关系")
    create_link("拆分为", "通行拟合路径_1", "拆分明细_1")
    create_link("拆分为", "通行拟合路径_1", "拆分明细_2")
    create_link("拆分为", "通行拟合路径_2", "拆分明细_3")
    print()
    
    print("创建关联拆分明细关系")
    create_link("关联拆分明细", "收费单元_南京-镇江段", "拆分明细_1")
    create_link("关联拆分明细", "收费单元_镇江-常州段", "拆分明细_2")
    create_link("关联拆分明细", "收费单元_上海-嘉兴段", "拆分明细_3")
    print()
    
    print("=" * 50)
    print("测试数据创建完成！")
    print("=" * 50)
    print()
    print(f"创建的实体总数: {len(ids)}")
    print()
    print("创建的实体类型：")
    entity_counts = {}
    for key in ids.keys():
        entity_type = key.split("_")[0]
        entity_counts[entity_type] = entity_counts.get(entity_type, 0) + 1
    
    for entity_type, count in sorted(entity_counts.items()):
        print(f"  - {entity_type}: {count}个")
    print()

if __name__ == "__main__":
    main()


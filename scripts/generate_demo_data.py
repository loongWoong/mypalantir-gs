#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
根据 schema.yaml 模型定义生成 MySQL 表结构和演示数据
"""

import yaml
import pymysql
from datetime import datetime, timedelta
import random
import string
from decimal import Decimal
import os
import sys

# 数据类型映射
TYPE_MAPPING = {
    'string': 'VARCHAR(255)',
    'integer': 'INT',
    'long': 'BIGINT',
    'date': 'DATE',
    'datetime': 'DATETIME',
    'double': 'DOUBLE',
    'bigdecimal': 'DECIMAL(18, 2)'
}

# 车牌颜色代码
PLATE_COLORS = [1, 2, 3, 4, 5]  # 1-蓝, 2-黄, 3-白, 4-黑, 5-绿

# 支付类型
PAY_TYPES = [1, 2, 3, 4, 5]  # 1-现金, 2-ETC, 3-支付宝, 4-微信, 5-银联

# 支付卡类型
PAY_CARD_TYPES = [1, 2, 3]  # 1-普通卡, 2-公务卡, 3-其他


def load_env():
    """从 .env 文件加载数据库配置"""
    env_path = os.path.join(os.path.dirname(os.path.dirname(__file__)), '.env')
    config = {}
    try:
        with open(env_path, 'r', encoding='utf-8') as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith('#') and '=' in line:
                    key, value = line.split('=', 1)
                    config[key.strip()] = value.strip()
    except FileNotFoundError:
        print(f"警告: 未找到 .env 文件: {env_path}")
        # 使用默认值
        config = {
            'DB_HOST': '49.233.67.173',
            'DB_PORT': '3306',
            'DB_NAME': 'HIGHLINKCLE113',
            'DB_USER': 'ump',
            'DB_PASSWORD': 'ump@2025',
            'DB_TYPE': 'mysql'
        }
    return config


def load_schema(schema_path):
    """加载 schema.yaml 文件"""
    with open(schema_path, 'r', encoding='utf-8') as f:
        return yaml.safe_load(f)


def get_mysql_type(prop):
    """将 schema 数据类型转换为 MySQL 类型"""
    data_type = prop.get('data_type', 'string').lower()
    return TYPE_MAPPING.get(data_type, 'VARCHAR(255)')


def generate_table_sql(object_type):
    """生成表的 CREATE TABLE SQL"""
    table_name = object_type['name']
    properties = object_type.get('properties', [])
    
    columns = []
    primary_keys = []
    
    for prop in properties:
        name = prop['name']
        data_type = get_mysql_type(prop)
        required = prop.get('required', False)
        
        col_def = f"`{name}` {data_type}"
        if not required:
            col_def += " DEFAULT NULL"
        col_def += f" COMMENT '{prop.get('display_name', name)}'"
        
        columns.append(col_def)
        
        if required and name in ['id', 'pass_id', 'transaction_id', 'interval_id', 'trade_id']:
            primary_keys.append(name)
    
    # 如果没有明确的主键，使用第一个 required 字段
    if not primary_keys:
        for prop in properties:
            if prop.get('required', False):
                primary_keys.append(prop['name'])
                break
    
    sql = f"CREATE TABLE IF NOT EXISTS `{table_name}` (\n"
    sql += ",\n".join(f"  {col}" for col in columns)
    
    if primary_keys:
        sql += f",\n  PRIMARY KEY (`{'`, `'.join(primary_keys)}`)"
    
    sql += f"\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='{object_type.get('display_name', table_name)}';"
    
    return sql


def generate_pass_id(index):
    """生成通行标识ID"""
    return f"PASS{index:010d}"


def generate_plate_number(index):
    """生成车牌号"""
    provinces = ['京', '沪', '粤', '苏', '浙', '鲁', '川', '渝', '湘', '鄂']
    letters = 'ABCDEFGHJKLMNPQRSTUVWXYZ'
    numbers = '0123456789'
    
    province = random.choice(provinces)
    letter = random.choice(letters)
    num = ''.join(random.choices(numbers, k=5))
    
    return f"{province}{letter}{num}"


def generate_random_string(length=10):
    """生成随机字符串"""
    return ''.join(random.choices(string.ascii_uppercase + string.digits, k=length))


def generate_random_date(start_date=None, days_offset=0):
    """生成随机日期"""
    if start_date is None:
        start_date = datetime.now() - timedelta(days=30)
    return (start_date + timedelta(days=days_offset)).date()


def generate_entry_transaction_data(index, pass_id, plate_num, plate_color):
    """生成入口车道流水数据"""
    en_time = generate_random_date()
    return {
        'id': f"ENTRY{index:010d}",
        'pass_id': pass_id,
        'l_date': en_time,
        'en_time': en_time,
        'station_receive_time': en_time,
        'receive_time': en_time,
        'oper_id': f"OP{random.randint(1000, 9999)}",
        'oper_name': f"操作员{random.randint(1, 100)}",
        'en_toll_lane_id': f"LANE{random.randint(1, 20):03d}",
        'media_type': random.choice([1, 2, 3]),
        'media_no': generate_random_string(16),
        'vlp': plate_num,
        'vlpc': plate_color,
        'identify_vlp': plate_num,
        'identify_vlpc': plate_color,
        'trans_code': generate_random_string(8),
        'trans_type': random.choice(['01', '02', '03']),
        'balance_before': random.randint(0, 100000),
        'trans_fee': random.randint(0, 50000),
        'direction': random.choice([1, 2]),
        'en_axle_count': random.choice([2, 4, 6]),
        'axis_info': f"{random.choice([2, 4, 6])}轴",
        'en_weight': str(random.randint(1000, 50000)),
        'limit_weight': random.randint(20000, 50000),
        'over_weight_rate': random.randint(0, 50),
        'description': '正常通行',
        'special_type': '',
        'lane_sp_info': '',
        'sp_info': ''
    }


def generate_exit_transaction_data(index, pass_id, plate_num, plate_color):
    """生成出口车道流水数据"""
    en_time = generate_random_date()
    ex_time = en_time + timedelta(hours=random.randint(1, 5))
    fee = random.randint(10000, 100000)
    discount = random.randint(0, fee // 10)
    
    return {
        'id': f"EXIT{index:010d}",
        'pass_id': pass_id,
        'source_id': f"SOURCE{index:010d}",
        'return_money_sn': '',
        'l_date': en_time,
        'ex_time': ex_time,
        'station_receive_time': ex_time,
        'receive_time': ex_time,
        'oper_id': f"OP{random.randint(1000, 9999)}",
        'oper_name': f"操作员{random.randint(1, 100)}",
        'ex_toll_lane_id': f"LANE{random.randint(1, 20):03d}",
        'ex_toll_station_id': f"ST{random.randint(1, 50):04d}",
        'ex_toll_station_name': f"收费站{random.randint(1, 50)}",
        'media_type': random.choice([1, 2, 3]),
        'media_no': generate_random_string(16),
        'ex_vlp': plate_num,
        'ex_vlpc': plate_color,
        'identify_vlp': plate_num,
        'identify_vlpc': plate_color,
        'trans_code': generate_random_string(8),
        'trans_type': random.choice(['01', '02', '03']),
        'multi_province': random.choice([0, 1]),
        'province_group': '',
        'toll_province_id': f"PROV{random.randint(1, 10):02d}",
        'trans_pay_type': random.choice(PAY_TYPES),
        'pay_type': random.choice(PAY_TYPES),
        'pay_card_id': generate_random_string(16),
        'axle_count': random.choice([2, 4, 6]),
        'axis_info': f"{random.choice([2, 4, 6])}轴",
        'ex_weight': random.randint(1000, 50000),
        'limit_weight': random.randint(20000, 50000),
        'over_weight_rate': random.randint(0, 50),
        'description': '正常通行',
        'v_speed': 0,
        'special_type': '',
        'lane_sp_info': '',
        'sp_info': '',
        'modify_flag': 0,
        'toll_distance': random.randint(10000, 200000),
        'real_distance': random.randint(10000, 200000),
        'free_type': 0,
        'free_mode': 0,
        'free_info': '',
        'trans_fee': fee,
        'balance_before': random.randint(0, 100000),
        'balance_after': random.randint(0, 100000),
        'fee': fee,
        'discount_fee': discount,
        'pay_fee': fee - discount,
        'obu_pay_fee': fee - discount,
        'obu_discount_fee': discount,
        'fee_mileage': random.randint(10, 200),
        'collect_fee': 0,
        'rebate_money': 0,
        'card_cost_fee': 0,
        'unpay_fee': 0,
        'unpay_flag': '',
        'unpay_card_cost': 0,
        'ticket_fee': 0,
        'en_toll_money': fee,
        'en_free_money': 0,
        'en_last_money': fee - discount
    }


def generate_gantry_transaction_data(index, pass_id, plate_num, plate_color):
    """生成ETC门架计费流水数据"""
    trans_time = generate_random_date()
    fee = random.randint(1000, 20000)
    discount = random.randint(0, fee // 10)
    
    return {
        'trade_id': f"GANTRY{index:010d}",
        'pass_id': pass_id,
        'trans_time': trans_time,
        'record_gen_time': trans_time,
        'receive_time': trans_time,
        'gantry_id': f"G{random.randint(1, 100):04d}",
        'gantry_type': random.choice(['A', 'B', 'C']),
        'original_flag': random.choice([0, 1]),
        'gantry_hex': generate_random_string(8),
        'last_gantry_hex': generate_random_string(8),
        'last_gantry_time': trans_time - timedelta(minutes=random.randint(5, 30)),
        'media_type': random.choice([1, 2, 3]),
        'cpu_card_id': generate_random_string(16),
        'vlp': plate_num,
        'vlpc': plate_color,
        'vehicle_type': random.choice([1, 2, 3, 4, 5]),
        'identify_vehicle_type': random.choice([1, 2, 3, 4, 5]),
        'trade_type': random.choice([1, 2, 3]),
        'axle_count': random.choice([2, 4, 6]),
        'total_weight': random.randint(1000, 50000),
        'vehicle_length': random.randint(400, 2000),
        'vehicle_width': random.randint(150, 300),
        'vehicle_hight': random.randint(150, 400),
        'fee_mileage': random.randint(1, 50),
        'trans_fee': fee,
        'balance_before': random.randint(0, 100000),
        'balance_after': random.randint(0, 100000),
        'pay_fee': fee - discount,
        'fee': fee,
        'discount_fee': discount,
        'toll_interval_id': f"INT{random.randint(1, 100):04d}",
        'toll_interval_sign': generate_random_string(4),
        'pay_fee_group': f"{fee - discount}",
        'fee_group': f"{fee}",
        'discount_fee_group': f"{discount}",
        'description': '门架计费',
        'fee_calc_special': 0,
        'charges_special_type': '',
        'is_fix_data': 0
    }


def generate_path_data(pass_id, plate_num, plate_color, en_time, ex_time):
    """生成车辆通行路径数据"""
    return {
        'pass_id': pass_id,
        'plate_num': plate_num,
        'plate_color': plate_color,
        'en_time': en_time,
        'ex_time': ex_time,
        'en_toll_lane_id': f"LANE{random.randint(1, 20):03d}",
        'ex_toll_lane_id': f"LANE{random.randint(1, 20):03d}",
        'en_toll_station_id': f"ST{random.randint(1, 50):04d}",
        'ex_toll_station_id': f"ST{random.randint(1, 50):04d}",
        'ex_vehicle_type': random.choice([1, 2, 3, 4, 5]),
        'pay_type': random.choice(PAY_TYPES),
        'pay_card_type': random.choice(PAY_CARD_TYPES),
        'l_date': en_time
    }


def generate_path_detail_data(index, pass_id, plate_num, plate_color):
    """生成车辆通行路径明细数据"""
    trans_time = generate_random_date()
    intervals = [f"INT{i:04d}" for i in range(1, random.randint(2, 5))]
    fees = [random.randint(1000, 10000) for _ in intervals]
    
    return {
        'id': f"PD{index:010d}",
        'pass_id': pass_id,
        'plate_num': plate_num,
        'plate_color': plate_color,
        'identify_point_id': f"POINT{random.randint(1, 100):04d}",
        'identify_point_hex': generate_random_string(8),
        'intervals': '|'.join(intervals),
        'fee': '|'.join(map(str, fees)),
        'pay_fee': '|'.join(map(str, [f - random.randint(0, f//10) for f in fees])),
        'discount_fee': '|'.join(map(str, [random.randint(0, f//10) for f in fees])),
        'trans_time': trans_time
    }


def generate_split_detail_data(pass_id, transaction_id, interval_id):
    """生成拆分明细数据"""
    fee = random.randint(1000, 10000)
    discount = random.randint(0, fee // 10)
    
    return {
        'pass_id': pass_id,
        'transaction_id': transaction_id,
        'interval_id': interval_id,
        'toll_interval_fee': str(fee),
        'toll_interval_pay_fee': str(fee - discount),
        'toll_interval_discount_fee': str(discount),
        'split_flag': random.choice([0, 1]),
        'pro_split_time': generate_random_date(),
        'pro_split_type': random.choice([1, 2, 3]),
        'split_remark': '正常拆分'
    }


def generate_section_data(index):
    """生成高速公路路段信息数据"""
    return {
        'id': f"SECTION{index:04d}",
        'road_id': f"ROAD{random.randint(1, 10):02d}",
        'name': f"路段{index}",
        'section_owner_id': f"OWNER{random.randint(1, 5):02d}",
        'type': random.choice([1, 2, 3]),
        'length': random.randint(10000, 100000),
        'start_stake_num': f"K{random.randint(0, 100)}",
        'start_lat': f"{30 + random.random():.6f}",
        'start_lng': f"{120 + random.random():.6f}",
        'end_stake_num': f"K{random.randint(100, 200)}",
        'end_lat': f"{30 + random.random():.6f}",
        'end_lng': f"{120 + random.random():.6f}",
        'tax': random.randint(0, 1000),
        'tax_rate': round(random.random() * 0.1, 4),
        'charge_type': random.choice([1, 2, 3]),
        'toll_roads': f"ROAD{random.randint(1, 10):02d}",
        'build_time': generate_random_date(),
        'start_time': generate_random_date(),
        'end_time': None,
        'operation': 1,
        'record_gentime': generate_random_date(),
        'status': 'ACTIVE'
    }


def generate_clear_result_data(pass_id, transaction_id, interval_id, crop_id, road_id):
    """生成清分结果数据"""
    amount = Decimal(random.randint(1000, 10000))
    discount = Decimal(random.randint(0, int(amount) // 10))
    
    return {
        'pass_id': pass_id,
        'transaction_id': transaction_id,
        'interval_id': interval_id,
        'crop_id': crop_id,
        'road_id': road_id,
        'pay_type': random.choice(PAY_TYPES),
        'pay_card_type': random.choice(PAY_CARD_TYPES),
        'amount': amount,
        'discount_amount': discount,
        'charge_amount': amount - discount,
        'clear_date': generate_random_date(),
        'l_date': generate_random_date()
    }


def generate_clear_report_data(crop_id, road_id, pay_card_type, clear_date, l_date):
    """生成清分报表数据"""
    base_amount = Decimal(random.randint(100000, 1000000))
    
    return {
        'crop_id': crop_id,
        'road_id': road_id,
        'split_org': f"ORG{random.randint(1, 10):02d}",
        'org_type': random.choice([1, 2, 3]),
        'pay_card_type': pay_card_type,
        'money_flag': random.choice([0, 1]),
        'clear_date': clear_date,
        'l_date': l_date,
        'gen_time': datetime.now(),
        'cash_split_money': base_amount * Decimal('0.2'),
        'cash_op_split_money': base_amount * Decimal('0.05'),
        'cash_toll_money': base_amount * Decimal('0.25'),
        'cash_op_toll_money': base_amount * Decimal('0.05'),
        'other_split_money': base_amount * Decimal('0.1'),
        'other_op_split_money': base_amount * Decimal('0.02'),
        'other_toll_money': base_amount * Decimal('0.12'),
        'other_op_toll_money': base_amount * Decimal('0.02'),
        'union_split_money': base_amount * Decimal('0.15'),
        'union_op_split_money': base_amount * Decimal('0.03'),
        'union_toll_money': base_amount * Decimal('0.18'),
        'union_op_toll_money': base_amount * Decimal('0.03'),
        'etc_split_money': base_amount * Decimal('0.3'),
        'etc_op_split_money': base_amount * Decimal('0.05'),
        'etc_toll_money': base_amount * Decimal('0.35'),
        'etc_op_toll_money': base_amount * Decimal('0.05'),
        'alipay_split_money': base_amount * Decimal('0.1'),
        'alipay_op_split_money': base_amount * Decimal('0.02'),
        'alipay_toll_money': base_amount * Decimal('0.12'),
        'alipay_op_toll_money': base_amount * Decimal('0.02'),
        'wepay_split_money': base_amount * Decimal('0.1'),
        'wepay_op_split_money': base_amount * Decimal('0.02'),
        'wepay_toll_money': base_amount * Decimal('0.12'),
        'wepay_op_toll_money': base_amount * Decimal('0.02'),
        'cash_return_money': Decimal(0),
        'other_return_money': Decimal(0),
        'union_return_money': Decimal(0),
        'etc_return_money': Decimal(0),
        'alipay_return_money': Decimal(0),
        'wepay_return_money': Decimal(0)
    }


def insert_data(cursor, table_name, data):
    """插入数据到数据库"""
    if not data:
        return
    
    # 过滤 None 值
    filtered_data = {k: v for k, v in data.items() if v is not None}
    
    if not filtered_data:
        return
    
    columns = ', '.join([f"`{k}`" for k in filtered_data.keys()])
    placeholders = ', '.join(['%s'] * len(filtered_data))
    values = list(filtered_data.values())
    
    sql = f"INSERT INTO `{table_name}` ({columns}) VALUES ({placeholders})"
    
    try:
        cursor.execute(sql, values)
    except Exception as e:
        print(f"插入数据到 {table_name} 时出错: {e}")
        print(f"SQL: {sql}")
        print(f"数据: {filtered_data}")


def main():
    """主函数"""
    # 加载配置
    config = load_env()
    schema_path = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'ontology', 'schema.yaml')
    
    # 加载 schema
    schema = load_schema(schema_path)
    object_types = schema.get('object_types', [])
    link_types = schema.get('link_types', [])
    
    # 连接数据库
    try:
        conn = pymysql.connect(
            host=config['DB_HOST'],
            port=int(config['DB_PORT']),
            user=config['DB_USER'],
            password=config['DB_PASSWORD'],
            database=config['DB_NAME'],
            charset='utf8mb4'
        )
        cursor = conn.cursor()
        print(f"成功连接到数据库: {config['DB_NAME']}")
    except Exception as e:
        print(f"连接数据库失败: {e}")
        sys.exit(1)
    
    try:
        # 1. 创建表
        print("\n=== 创建表结构 ===")
        for obj_type in object_types:
            table_name = obj_type['name']
            sql = generate_table_sql(obj_type)
            try:
                cursor.execute(sql)
                print(f"✓ 创建表: {table_name}")
            except Exception as e:
                print(f"✗ 创建表 {table_name} 失败: {e}")
        
        conn.commit()
        
        # 2. 生成演示数据
        print("\n=== 生成演示数据 ===")
        
        # 先生成基础数据：Section
        sections = []
        for i in range(1, 6):
            section_data = generate_section_data(i)
            sections.append(section_data)
            insert_data(cursor, 'Section', section_data)
        print(f"✓ 生成 {len(sections)} 条 Section 数据")
        
        # 生成交易数据
        num_transactions = 10
        
        for i in range(1, num_transactions + 1):
            pass_id = generate_pass_id(i)
            plate_num = generate_plate_number(i)
            plate_color = random.choice(PLATE_COLORS)
            
            # EntryTransaction
            entry_data = generate_entry_transaction_data(i, pass_id, plate_num, plate_color)
            insert_data(cursor, 'EntryTransaction', entry_data)
            
            # ExitTransaction
            exit_data = generate_exit_transaction_data(i, pass_id, plate_num, plate_color)
            insert_data(cursor, 'ExitTransaction', exit_data)
            
            # GantryTransaction (每个通行标识生成2-3个门架交易)
            num_gantries = random.randint(2, 3)
            for j in range(num_gantries):
                gantry_data = generate_gantry_transaction_data(i * 10 + j, pass_id, plate_num, plate_color)
                insert_data(cursor, 'GantryTransaction', gantry_data)
            
            # Path
            en_time = entry_data['en_time']
            ex_time = exit_data['ex_time']
            path_data = generate_path_data(pass_id, plate_num, plate_color, en_time, ex_time)
            insert_data(cursor, 'Path', path_data)
            
            # PathDetail (每个路径生成2-3个明细)
            num_details = random.randint(2, 3)
            for j in range(num_details):
                path_detail_data = generate_path_detail_data(i * 10 + j, pass_id, plate_num, plate_color)
                insert_data(cursor, 'PathDetail', path_detail_data)
            
            # SplitDetail (每个路径生成3-5个拆分明细)
            num_splits = random.randint(3, 5)
            transaction_id = exit_data['id']
            for j in range(num_splits):
                interval_id = f"INT{j+1:04d}"
                split_data = generate_split_detail_data(pass_id, transaction_id, interval_id)
                insert_data(cursor, 'SplitDetail', split_data)
                
                # ClearResult
                section = random.choice(sections)
                crop_id = int(section['section_owner_id'].replace('OWNER', ''))
                road_id = section['road_id']
                clear_result_data = generate_clear_result_data(
                    pass_id, transaction_id, interval_id, crop_id, road_id
                )
                insert_data(cursor, 'ClearResult', clear_result_data)
        
        print(f"✓ 生成 {num_transactions} 组交易数据")
        
        # 生成 ClearReport (基于 ClearResult 汇总)
        print("✓ 生成 ClearReport 数据...")
        cursor.execute("""
            SELECT DISTINCT crop_id, road_id, pay_card_type, clear_date, l_date
            FROM ClearResult
            LIMIT 5
        """)
        
        for row in cursor.fetchall():
            crop_id, road_id, pay_card_type, clear_date, l_date = row
            report_data = generate_clear_report_data(crop_id, road_id, pay_card_type, clear_date, l_date)
            insert_data(cursor, 'ClearReport', report_data)
        
        conn.commit()
        print("\n✓ 所有数据生成完成！")
        
        # 显示统计信息
        print("\n=== 数据统计 ===")
        for obj_type in object_types:
            table_name = obj_type['name']
            cursor.execute(f"SELECT COUNT(*) FROM `{table_name}`")
            count = cursor.fetchone()[0]
            print(f"{obj_type.get('display_name', table_name)}: {count} 条")
        
    except Exception as e:
        conn.rollback()
        print(f"错误: {e}")
        import traceback
        traceback.print_exc()
    finally:
        cursor.close()
        conn.close()
        print("\n数据库连接已关闭")


if __name__ == '__main__':
    main()

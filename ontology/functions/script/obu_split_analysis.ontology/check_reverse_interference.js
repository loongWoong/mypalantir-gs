// 判断是否存在门架交易反向干扰（special_type=186 或 154）
function run(gantry_transactions) {
  if (gantry_transactions == null || typeof gantry_transactions.length !== 'number') return false;

  var list = [];
  for (var s = 0; s < gantry_transactions.length; s++) list.push(gantry_transactions[s]);

  for (var i = 0; i < list.length; i++) {
    var tx = list[i];
    if (tx == null) continue;

    // YAML 中字段名为 special_type，但历史数据/中间层可能出现 camelCase
    var st = tx.special_type != null ? tx.special_type : tx.specialType;
    if (st == null) continue;

    var stStr = String(st);
    if (stStr === '186' || stStr === '154') return true;
  }

  return false;
}


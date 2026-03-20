// 判断单条门架交易流水是否有效
// 一类流水：mediatype=1(OBU), traderesult=0(成功), feecalcresult=0(正常), validstatus=1 或 (validstatus=0 且 tac 非空且长度8)
// 二类流水：mediatype=1(OBU), traderesult=1(失败), feecalcresult=0(正常), validstatus=0 或 1
// 有效CPC：mediatype=2(CPC), traderesult=0(成功), feecalcresult=0(正常), validstatus=1 或 0
function run(gantry_transaction) {
  if (gantry_transaction == null) return false;
  var mt = gantry_transaction.mediatype != null ? Number(gantry_transaction.mediatype) : null;
  var tr = gantry_transaction.traderesult != null ? Number(gantry_transaction.traderesult) : null;
  var fcr = gantry_transaction.feecalcresult != null ? Number(gantry_transaction.feecalcresult) : null;
  var vs = gantry_transaction.validstatus != null ? Number(gantry_transaction.validstatus) : null;
  var tac = gantry_transaction.tac != null ? String(gantry_transaction.tac) : '';

  // 一类流水
  if (mt === 1 && tr === 0 && fcr === 0) {
    if (vs === 1) return true;
    if (vs === 0 && tac.length === 8) return true;
  }
  // 二类流水
  if (mt === 1 && tr === 1 && fcr === 0 && (vs === 0 || vs === 1)) return true;
  // 有效CPC流水
  if (mt === 2 && tr === 0 && fcr === 0 && (vs === 0 || vs === 1)) return true;

  return false;
}

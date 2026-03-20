// 检查通行记录中是否存在无效的门架交易流水
// 若存在任意一条无效，返回 true（has_invalid_gantry = true）
function run(passage) {
  if (passage == null) return false;
  var txList = passage.links != null ? passage.links.has_gantry_transaction : null;
  if (txList == null || typeof txList.length !== 'number') return false;

  for (var i = 0; i < txList.length; i++) {
    var valid = is_valid_gantry_transaction(txList[i]);
    if (!valid) return true; // 存在无效
  }
  return false;
}

function is_valid_gantry_transaction(gt) {
  if (gt == null) return false;
  var mt = gt.mediatype != null ? Number(gt.mediatype) : null;
  var tr = gt.traderesult != null ? Number(gt.traderesult) : null;
  var fcr = gt.feecalcresult != null ? Number(gt.feecalcresult) : null;
  var vs = gt.validstatus != null ? Number(gt.validstatus) : null;
  var tac = gt.tac != null ? String(gt.tac) : '';
  if (mt === 1 && tr === 0 && fcr === 0) {
    if (vs === 1) return true;
    if (vs === 0 && tac.length === 8) return true;
  }
  if (mt === 1 && tr === 1 && fcr === 0 && (vs === 0 || vs === 1)) return true;
  if (mt === 2 && tr === 0 && fcr === 0 && (vs === 0 || vs === 1)) return true;
  return false;
}

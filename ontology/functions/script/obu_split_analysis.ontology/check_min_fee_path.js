// 判断通行记录是否按最小费额路径计费
// 基于关联的在线计费记录：calculateResult=1 且 resDes 包含"按最小费额返回"
function run(passage) {
  if (passage == null) return false;
  var records = passage.links != null ? passage.links.has_online_charge_record : null;
  if (records == null || typeof records.length !== 'number') return false;

  for (var i = 0; i < records.length; i++) {
    var r = records[i];
    var calcRes = r.calculate_result != null ? Number(r.calculate_result) : null;
    var resDes = r.res_des != null ? String(r.res_des) : '';
    if (calcRes === 1 && resDes.indexOf('按最小费额返回') >= 0) return true;
  }
  return false;
}

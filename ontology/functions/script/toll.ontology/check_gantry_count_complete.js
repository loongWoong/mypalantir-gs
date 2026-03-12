function run(gantry_transactions) {
  if (gantry_transactions == null || !Array.isArray(gantry_transactions) || gantry_transactions.length === 0) return false;
  var list = gantry_transactions.slice();
  list.sort(function (a, b) {
    var ta = a.record_gen_time != null ? (typeof a.record_gen_time === 'string' ? new Date(a.record_gen_time).getTime() : (a.record_gen_time.getTime ? a.record_gen_time.getTime() : Number(a.record_gen_time))) : 0;
    var tb = b.record_gen_time != null ? (typeof b.record_gen_time === 'string' ? new Date(b.record_gen_time).getTime() : (b.record_gen_time.getTime ? b.record_gen_time.getTime() : Number(b.record_gen_time))) : 0;
    return ta - tb;
  });
  var last = list[list.length - 1];
  var totalSuccess = Number(last.obu_prov_trade_succ_num_after);
  if (isNaN(totalSuccess)) return false;
  return list.length === totalSuccess;
}

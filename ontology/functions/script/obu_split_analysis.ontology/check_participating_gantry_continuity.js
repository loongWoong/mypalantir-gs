// 兼容 Java List：Array.isArray() 对 Java List 为 false
function run(gantry_transactions) {
  if (gantry_transactions == null || typeof gantry_transactions.length !== 'number') return true;

  function toMillis(x) {
    if (x == null) return NaN;
    if (typeof x === 'number') return x;
    if (typeof x === 'string') return new Date(x).getTime();
    if (x.getTime) return x.getTime(); // JS Date / java.util.Date / Timestamp 等
    // java.time.LocalDateTime 等：优先用 toString() 再解析
    try {
      var s = String(x);
      var ms = new Date(s).getTime();
      if (!isNaN(ms)) return ms;
    } catch (e) {}
    return Number(x);
  }

  var participating = [];
  for (var i = 0; i < gantry_transactions.length; i++) {
    var flag = gantry_transactions[i].in_split_flag;
    // in_split_flag 允许出现：1/'1'/true/'true'
    if (flag === 1 || flag === '1' || flag === true || flag === 'true') {
      participating.push(gantry_transactions[i]);
    }
  }

  // 参与拆分的门架交易数量不足时，不应被判定为“连续”
  // 否则会把“参与门架路径不完整”场景错误地忽略掉。
  if (participating.length <= 1) return false;

  participating.sort(function (a, b) {
    var ta = toMillis(a.trans_time);
    var tb = toMillis(b.trans_time);
    if (isNaN(ta)) ta = 0;
    if (isNaN(tb)) tb = 0;
    return ta - tb;
  });

  for (var j = 1; j < participating.length; j++) {
    var lastHex = participating[j].last_gantry_hex;
    var prevHex = participating[j - 1].gantry_hex;
    if (lastHex == null || prevHex == null) return false;
    // HEX 可能大小写不一致，按不区分大小写比较
    if (String(lastHex).toUpperCase() !== String(prevHex).toUpperCase()) return false;
  }
  return true;
}

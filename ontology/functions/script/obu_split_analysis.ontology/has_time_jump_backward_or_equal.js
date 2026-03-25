// 判断物理路径连续的门架之间是否存在时间倒流或相等
// 物理路径关系：交易[j].gantry_hex == 交易[i].last_gantry_hex 表示 j 在 i 之后经过
// 若 txs[j].trans_time <= txs[i].trans_time，则视为时间倒流（或时间相等）异常
function run(gantry_transactions) {
  if (gantry_transactions == null || typeof gantry_transactions.length !== 'number') return false;

  var list = [];
  for (var s = 0; s < gantry_transactions.length; s++) list.push(gantry_transactions[s]);

  for (var i = 0; i < list.length; i++) {
    var hexI = list[i].gantry_hex;
    if (hexI == null) continue;
    var timeI = list[i].trans_time;
    if (timeI == null) continue;
    var timeIMs = typeof timeI === 'string'
      ? new Date(timeI).getTime()
      : (timeI.getTime ? timeI.getTime() : Number(timeI));
    if (!isFinite(timeIMs)) continue;

    for (var j = 0; j < list.length; j++) {
      if (i === j) continue;
      var lastHexJ = list[j].last_gantry_hex;
      if (lastHexJ == null) continue;
      if (String(lastHexJ) !== String(hexI)) continue; // j 的上一门架是 i

      var timeJ = list[j].trans_time;
      if (timeJ == null) continue;
      var timeJMs = typeof timeJ === 'string'
        ? new Date(timeJ).getTime()
        : (timeJ.getTime ? timeJ.getTime() : Number(timeJ));
      if (!isFinite(timeJMs)) continue;

      // 含相等：业务语义 transtime >= i+1 transtime -> 异常
      if (timeJMs <= timeIMs) return true;
    }
  }

  return false;
}


// 判断物理路径连续的门架之间是否存在时间倒流
// 物理路径关系：交易j的gantryHex == 交易i的lastGantryHex 表示 j 在 i 之后经过
// 若 txs[j].transTime < txs[i].transTime 则时间倒流
function run(gantry_transactions) {
  if (gantry_transactions == null || typeof gantry_transactions.length !== 'number') return false;

  var list = [];
  for (var s = 0; s < gantry_transactions.length; s++) list.push(gantry_transactions[s]);

  for (var i = 0; i < list.length; i++) {
    var hexI = list[i].gantry_hex;
    if (hexI == null) continue;
    var timeI = list[i].trans_time;
    if (timeI == null) continue;
    var timeIMs = typeof timeI === 'string' ? new Date(timeI).getTime() : (timeI.getTime ? timeI.getTime() : Number(timeI));

    for (var j = 0; j < list.length; j++) {
      if (i === j) continue;
      var lastHexJ = list[j].last_gantry_hex;
      if (lastHexJ == null) continue;
      if (String(lastHexJ) !== String(hexI)) continue; // j 的上一门架是 i，即 j 在 i 之后

      var timeJ = list[j].trans_time;
      if (timeJ == null) continue;
      var timeJMs = typeof timeJ === 'string' ? new Date(timeJ).getTime() : (timeJ.getTime ? timeJ.getTime() : Number(timeJ));
      if (timeJMs < timeIMs) return true; // 后经过的门架时间反而早，时间倒流
    }
  }
  return false;
}

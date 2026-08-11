// Turns an IBKR /portfolio/{accountId}/summary response into a stability rating.
function num(field) {
  return field && typeof field.amount === 'number' ? field.amount : null;
}

function assessAccountStability(summary) {
  const netLiquidation = num(summary.netliquidation);
  const excessLiquidity = num(summary.excessliquidity);
  const maintMarginReq = num(summary.maintmarginreq);
  const grossPositionValue = num(summary.grosspositionvalue);
  const buyingPower = num(summary.buyingpower);

  const metrics = {
    netLiquidation,
    excessLiquidity,
    maintMarginReq,
    grossPositionValue,
    buyingPower,
    marginCushionPct: netLiquidation ? (excessLiquidity / netLiquidation) * 100 : null,
    maintMarginUsagePct: netLiquidation ? (maintMarginReq / netLiquidation) * 100 : null,
    leverageRatio: netLiquidation ? grossPositionValue / netLiquidation : null,
  };

  let rating = 'UNKNOWN';
  const reasons = [];

  if (metrics.marginCushionPct !== null && metrics.maintMarginUsagePct !== null) {
    if (metrics.marginCushionPct < 15 || metrics.maintMarginUsagePct > 60) {
      rating = 'AT_RISK';
      reasons.push('Low margin cushion relative to net liquidation value — a market move against your positions could trigger a margin call.');
    } else if (metrics.marginCushionPct < 35 || metrics.maintMarginUsagePct > 35) {
      rating = 'CAUTION';
      reasons.push('Margin usage is moderate — worth monitoring during volatile sessions.');
    } else {
      rating = 'HEALTHY';
      reasons.push('Ample margin cushion relative to account size.');
    }
  }

  if (metrics.leverageRatio !== null && metrics.leverageRatio > 2) {
    reasons.push(`Leverage ratio is ${metrics.leverageRatio.toFixed(2)}x — positions are large relative to equity.`);
    if (rating === 'HEALTHY') rating = 'CAUTION';
  }

  return { rating, metrics, reasons };
}

module.exports = { assessAccountStability };

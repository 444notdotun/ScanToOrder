// What a guest reads for each step of an order.
export function orderStatusLabel(status: string): string {
  switch (status) {
    case 'PENDING_PAYMENT':
      return 'Waiting for payment';
    case 'PAID':
      return 'With the kitchen';
    case 'PREPARING':
      return 'Cooking';
    case 'READY':
      return 'Ready. A waiter is bringing it over';
    case 'DELIVERED':
      return 'Delivered';
    case 'UNAVAILABLE':
      return "The kitchen can't make this. A waiter is coming to you";
    case 'REFUND_DUE':
      return 'The restaurant owes you a refund';
    case 'REFUNDED':
      return 'Refunded';
    case 'CANCELED':
      return 'Cancelled';
    default:
      return status;
  }
}

// "K7M2QD" is easier to read aloud as "K7M 2QD".
export function spacedCode(code: string): string {
  const clean = code.replace(/\s+/g, '');
  return clean.length === 6 ? `${clean.slice(0, 3)} ${clean.slice(3)}` : clean;
}

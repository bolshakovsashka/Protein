package tracking

import protein.tracking.ErrorTracking

class ConsoleLogTracking : ErrorTracking {
  override fun logException(throwable: Throwable) {
    throwable.printStackTrace()
    System.out.print(throwable.message)
  }
}
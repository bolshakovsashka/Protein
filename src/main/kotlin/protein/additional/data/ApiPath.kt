package protein.additional.data

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class ApiPath(

  @SerializedName("type")
  @Expose
  var type: String = "",

  @SerializedName("path")
  @Expose
  var path: String? = null

)

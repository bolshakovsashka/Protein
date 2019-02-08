package protein.additional.data

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class AdditionalMethod(
  @SerializedName("api_paths")
  @Expose
  val apiPaths: List<ApiPath> = emptyList(),

  @SerializedName("method_name")
  @Expose
  val methodName: String = "",

  @SerializedName("return_type")
  @Expose
  val returnType: String = "",

  @SerializedName("parameters")
  @Expose
  val parameters: List<Parameter> = emptyList()
)
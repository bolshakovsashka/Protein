package protein.additional.data

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class Config(
  @SerializedName("additional_methods")
  @Expose
  val additionalMethods: List<AdditionalMethod> = emptyList()
)
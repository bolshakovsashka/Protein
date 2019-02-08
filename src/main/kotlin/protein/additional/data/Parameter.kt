package protein.additional.data

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class Parameter(

    @SerializedName("annotation")
    @Expose
    var annotation: String = "",

    @SerializedName("type")
    @Expose
    var type: String = "",

    @SerializedName("name")
    @Expose
    var name: String =""
)
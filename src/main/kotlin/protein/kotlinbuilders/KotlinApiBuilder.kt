package protein.kotlinbuilders

import com.google.gson.annotations.SerializedName
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asClassName
import io.reactivex.Completable
import io.reactivex.Single
import io.swagger.models.*
import io.swagger.models.parameters.BodyParameter
import io.swagger.models.parameters.Parameter
import io.swagger.models.parameters.PathParameter
import io.swagger.models.parameters.QueryParameter
import io.swagger.models.properties.ArrayProperty
import io.swagger.models.properties.DoubleProperty
import io.swagger.models.properties.FloatProperty
import io.swagger.models.properties.IntegerProperty
import io.swagger.models.properties.LongProperty
import io.swagger.models.properties.Property
import io.swagger.models.properties.RefProperty
import io.swagger.models.properties.StringProperty
import io.swagger.parser.SwaggerParser
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import protein.additional.data.ApiPath
import protein.common.StorageUtils
import protein.tracking.ErrorTracking
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url
import java.io.FileNotFoundException
import java.net.UnknownHostException
import java.util.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.Iterable
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableMap
import kotlin.collections.any
import kotlin.collections.filterNot
import kotlin.collections.forEach
import kotlin.collections.iterator
import kotlin.collections.joinToString
import kotlin.collections.listOf
import kotlin.collections.map
import kotlin.collections.mapIndexed
import kotlin.collections.mapNotNull
import kotlin.collections.plus
import kotlin.collections.set

class KotlinApiBuilder(
  private val proteinApiConfiguration: ProteinApiConfiguration,
  private val errorTracking: ErrorTracking
) {
  companion object {
    const val OK_RESPONSE = "200"
    const val ARRAY_SWAGGER_TYPE = "array"
    const val INTEGER_SWAGGER_TYPE = "integer"
    const val NUMBER_SWAGGER_TYPE = "number"
    const val STRING_SWAGGER_TYPE = "string"
    const val BOOLEAN_SWAGGER_TYPE = "boolean"
    const val REF_SWAGGER_TYPE = "ref"


    const val PREFIX_API = "api"
    const val PREFIX_MODELS = "models"
    const val PREFIX_DTO = "dto"
    const val PREFIX_COMMON = "common"
  }

  private val swaggerModel: Swagger = try {
    val sw = if (!proteinApiConfiguration.swaggerUrl.isEmpty()) {
      SwaggerParser().read(proteinApiConfiguration.swaggerUrl)
    } else {
      SwaggerParser().read(proteinApiConfiguration.swaggerFile)
    }

    getAdditionSwaggerModel()?.let {
      it.tags?.forEach { sw.addTag(it) }
      it.definitions?.forEach { t, u -> sw.addDefinition(t, u) }
      it.paths?.forEach { t, u -> sw.path(t, u) }
    }

    sw
  } catch (unknown: UnknownHostException) {
    errorTracking.logException(unknown)
    Swagger()
  } catch (illegal: IllegalStateException) {
    errorTracking.logException(illegal)
    Swagger()
  } catch (notFound: FileNotFoundException) {
    errorTracking.logException(notFound)
    Swagger()
  }

  private lateinit var apiInterfaceTypeSpec: Map<String, TypeSpec>
  private val responseBodyModelListTypeSpec: ArrayList<TypeSpec> = ArrayList()
  private val enumListTypeSpec: ArrayList<TypeSpec> = ArrayList()

  private fun getAdditionSwaggerModel(): Swagger? = try {
    if (proteinApiConfiguration.additionalConfig.isNotEmpty()) {
      val swagger = SwaggerParser().read(proteinApiConfiguration.additionalConfig)
      swagger
    } else {
      null
    }
  } catch (t: Throwable) {
    t.printStackTrace()
    null
  }

  fun build() {
    createEnumClasses()
    apiInterfaceTypeSpec = createApiRetrofitInterface(createApiResponseBodyModel())
  }

  fun generateFiles() {

    val cl = ArrayList<TypeSpecWrapper>()

    responseBodyModelListTypeSpec.forEach check@{ typeSpec ->
      var packageName = PREFIX_DTO
      try {
        apiInterfaceTypeSpec.forEach { pack, apiTypeSpec ->
          apiTypeSpec.funSpecs.forEach { apiFunSpec ->
            (apiFunSpec.returnType as? ParameterizedTypeName)?.typeArguments?.forEach {
              if ((it as? TypeVariableName)?.name?.equals(typeSpec.name, true) == true) {
                packageName = "$pack"
              }
            }
            apiFunSpec.parameters.forEach { parameterSpec ->
              if ((parameterSpec.type as? ClassName)?.canonicalName?.equals(typeSpec.name, true) == true) {
                packageName = "$pack"
              }
            }
          }
        }
      } catch (e: Exception) {
//          e.printStackTrace()
      }

      cl.add(TypeSpecWrapper(packageName, typeSpec))
    }

    cl.forEach { typeSpecWrapper ->
      updatePackage(typeSpecWrapper, cl)
    }

    cl.forEach {
      if (it.subPackage == PREFIX_DTO) {
        it.subPackage = PREFIX_COMMON
      }
    }

    cl.forEach {
      val imports = generateImports(it.typeSpec, cl)
      StorageUtils.generateFiles(
        proteinApiConfiguration.moduleName, proteinApiConfiguration.packageName, "${it.subPackage}.$PREFIX_MODELS", it.typeSpec, imports.toTypedArray())
    }

    apiInterfaceTypeSpec.forEach { t, u ->
      val imports = generateImports(u, cl)

      StorageUtils.generateFiles(
        proteinApiConfiguration.moduleName, proteinApiConfiguration.packageName, "$t.$PREFIX_API", u, imports.toTypedArray())
    }

    for (typeSpec in enumListTypeSpec) {
      StorageUtils.generateFiles(
        proteinApiConfiguration.moduleName, proteinApiConfiguration.packageName, null, typeSpec, emptyArray())
    }
  }

  private fun generateImports(u: TypeSpec, cl: ArrayList<TypeSpecWrapper>): ArrayList<String> {
    val imports = ArrayList<String>()
    try {
      u.funSpecs.forEach { funSpec ->
        (funSpec.returnType as? ParameterizedTypeName)?.typeArguments?.forEach {
          val name = (it as? TypeVariableName)?.name
          val find = cl.find { pair -> (pair.typeSpec.name?.equals(name, true)) == true }
          if (null != find) {
            imports.add("${find.subPackage}.$PREFIX_MODELS.${find.typeSpec.name}")
          }
        }
        funSpec.parameters.forEach { parameterSpec ->
          val name = (parameterSpec.type as? ClassName)?.canonicalName
          val find = cl.find { pair -> (pair.typeSpec.name?.equals(name, true)) == true }
          if (null != find) {
            imports.add("${find.subPackage}.$PREFIX_MODELS.${find.typeSpec.name}")
          }
        }
      }
      u.propertySpecs.forEach {
        (it.type as? ParameterizedTypeName)?.typeArguments?.forEach { parameterizedTypeName: TypeName ->
          val name = (parameterizedTypeName as? TypeVariableName)?.name
          val find = cl.find { it.typeSpec.name?.equals(name) == true }
          if (null != find) {
            imports.add("${find.subPackage}.$PREFIX_MODELS.${find.typeSpec.name}")
          }
        }

        val name = (it.type as? TypeVariableName)?.name
        val find = cl.find { pair -> (pair.typeSpec.name?.equals(name, true)) == true }
        if (null != find) {
          imports.add("${find.subPackage}.$PREFIX_MODELS.${find.typeSpec.name}")
        }
      }
      if((u.superclass as TypeVariableName).name != "Any"){
        val name = (u.superclass as TypeVariableName).name
        val find = cl.find { pair -> (pair.typeSpec.name?.equals(name, true)) == true }
        if (find != null) {
          imports.add("${find.subPackage}.$PREFIX_MODELS.${find.typeSpec.name}")
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
    return imports
  }

  private fun updatePackage(typeSpecWrapper: TypeSpecWrapper, cl: ArrayList<TypeSpecWrapper>) {
    var subPackage = typeSpecWrapper.subPackage
    if (subPackage != PREFIX_DTO) {
      typeSpecWrapper.typeSpec.propertySpecs.forEach { propertySpec ->
        (propertySpec.type as? ParameterizedTypeName)?.typeArguments?.forEach { parameterizedTypeName: TypeName ->
          val name = (parameterizedTypeName as? TypeVariableName)?.name
          val find = cl.find { it.typeSpec.name?.equals(name) == true }
          if (null != find) {
            if (find.subPackage == PREFIX_DTO || find.subPackage == typeSpecWrapper.subPackage) {
              find.subPackage = typeSpecWrapper.subPackage
            } else {
              find.subPackage = PREFIX_COMMON
            }
            updatePackage(find, cl)
          }
        }
        val name = (propertySpec.type as? TypeVariableName)?.name
        val find = cl.find { it.typeSpec.name?.equals(name) == true }
        if (null != find) {
          if (find.subPackage == PREFIX_DTO || find.subPackage == typeSpecWrapper.subPackage) {
            find.subPackage = typeSpecWrapper.subPackage
          } else {
            find.subPackage = PREFIX_COMMON
          }
          updatePackage(find, cl)
        }
      }
    }
  }

  private data class TypeSpecWrapper(var subPackage: String, val typeSpec: TypeSpec)

  private fun createEnumClasses() {
    addOperationResponseEnums()
    addModelEnums()
  }

  private fun addModelEnums() {
    if (swaggerModel.definitions != null && !swaggerModel.definitions.isEmpty()) {
      for (definition in swaggerModel.definitions) {
        if (definition.value != null && definition.value.properties != null) {
          for (modelProperty in definition.value.properties) {
            if (modelProperty.value is StringProperty) {
              val enumDefinition = (modelProperty.value as StringProperty).enum
              if (enumDefinition != null) {
                val enumTypeSpecBuilder = TypeSpec.enumBuilder(modelProperty.key.capitalize())
                for (constant in enumDefinition) {
                  enumTypeSpecBuilder.addEnumConstant(constant)
                }
                if (!enumListTypeSpec.contains(enumTypeSpecBuilder.build())) {
                  enumListTypeSpec.add(enumTypeSpecBuilder.build())
                }
              }
            }
          }
        }
      }
    }
  }

  private fun addOperationResponseEnums() {
    if (swaggerModel.paths != null && !swaggerModel.paths.isEmpty()) {
      for (path in swaggerModel.paths) {
        for (operation in path.value.operationMap) {
          try {
            for (parameters in operation.value.parameters) {
              if (parameters is PathParameter) {
                if (parameters.enum != null) {
                  val enumTypeSpecBuilder = TypeSpec.enumBuilder(parameters.name.capitalize())
                  for (constant in parameters.enum) {
                    enumTypeSpecBuilder.addEnumConstant(constant)
                  }
                  if (!enumListTypeSpec.contains(enumTypeSpecBuilder.build())) {
                    enumListTypeSpec.add(enumTypeSpecBuilder.build())
                  }
                }
              }
            }
          } catch (error: Exception) {
            errorTracking.logException(error)
          }
        }
      }
    }
  }

  private fun createApiResponseBodyModel(): List<String> {
    val classNameList = ArrayList<String>()

    swaggerModel.definitions?.forEach { definition ->

      var modelClassTypeSpec: TypeSpec.Builder
      try {
        modelClassTypeSpec = TypeSpec.classBuilder(definition.key)
        classNameList.add(definition.key)
      } catch (error: IllegalArgumentException) {
        modelClassTypeSpec = TypeSpec.classBuilder("Model" + definition.key.capitalize()).addModifiers(KModifier.DATA)
        classNameList.add("Model" + definition.key.capitalize())
      }

      if (definition.value != null) {
        val primaryConstructor = FunSpec.constructorBuilder()
        if (definition.value.properties != null) {

          for (modelProperty in definition.value.properties) {
            val typeName: TypeName = getTypeName(modelProperty)
            val propertySpec = PropertySpec.builder(modelProperty.key, typeName)
              .addAnnotation(AnnotationSpec.builder(SerializedName::class)
                .addMember("\"${modelProperty.key}\"")
                .build())
              .initializer(modelProperty.key)
              .build()
            primaryConstructor.addParameter(modelProperty.key, typeName)
            modelClassTypeSpec.addProperty(propertySpec)

          }
        } else if (definition.value is ComposedModel) {

          val composedModel: ComposedModel = (definition.value as ComposedModel)
          if (composedModel.interfaces.isNotEmpty()) {
            addParentParams(composedModel, modelClassTypeSpec, primaryConstructor)
          }
          if (composedModel.child.properties != null) {
            addSelfProperties(composedModel, primaryConstructor, modelClassTypeSpec)
          }
        }

        if (hasChilds(definition, swaggerModel.definitions)) {
          modelClassTypeSpec.addModifiers(KModifier.OPEN)
        } else {
          modelClassTypeSpec.addModifiers(KModifier.DATA)
        }

        modelClassTypeSpec.primaryConstructor(primaryConstructor.build())
        responseBodyModelListTypeSpec.add(modelClassTypeSpec.build())
      }
    }


    return classNameList
  }

  private fun hasChilds(modelToCheck: Map.Entry<String, Model>, definitions: MutableMap<String, Model>): Boolean {
    modelToCheck.value.let {
      definitions.forEach { definition ->
        if (definition.value is ComposedModel) {
          val composedModel = (definition.value as ComposedModel);
          for (interfaceModel in composedModel.interfaces) {
            if (interfaceModel.simpleRef == modelToCheck.key) {
              return true
            }
          }
        }
      }
    }
    return false
  }

  private fun addParentParams(composedModel: ComposedModel, modelClassTypeSpec: TypeSpec.Builder, primaryConstructor: FunSpec.Builder) {
    val parent = composedModel.interfaces[0]

    parent?.simpleRef?.let {
      modelClassTypeSpec.superclass(TypeVariableName.invoke(it))
      val parentProperties = getParentProperties(swaggerModel.definitions, it)

      for (property in parentProperties) {
        val typeName: TypeName = getTypeName(property)
        primaryConstructor.addParameter(property.key, typeName)
        modelClassTypeSpec.addSuperclassConstructorParameter(property.key, typeName)
      }
    }
  }

  private fun addSelfProperties(composedModel: ComposedModel, primaryConstructor: FunSpec.Builder, modelClassTypeSpec: TypeSpec.Builder) {
    for (property in composedModel.child.properties) {
      val typeName: TypeName = getTypeName(property)
      val propertySpec = PropertySpec.builder(property.key, typeName)
        .addAnnotation(AnnotationSpec.builder(SerializedName::class)
          .addMember("\"${property.key}\"")
          .build())
        .initializer(property.key)
        .build()

      primaryConstructor.addParameter(property.key, typeName)
      modelClassTypeSpec.addProperty(propertySpec)
    }
  }

  private fun getParentProperties(definitions: Map<String, Model>, parentName: String?): Map<String, Property> {
    val resultProperties = mutableMapOf<String, Property>()
    parentName?.let {
      val model = definitions[it]

      val properties = model?.properties
      if (properties != null) {
        resultProperties.putAll(properties)
      } else if (model is ComposedModel) {
        resultProperties.putAll(model.child.properties)
        if (!model.interfaces.isEmpty()) {
          val parent = model.interfaces[0].simpleRef
          resultProperties.putAll(getParentProperties(definitions, parent))
        }
        return resultProperties
      }
    }
    return resultProperties
  }

  private fun createApiRetrofitInterface(classNameList: List<String>): Map<String, TypeSpec> {
    val map: HashMap<String, TypeSpec> = HashMap()
    swaggerModel.tags.forEach {

      val apiInterfaceTypeSpecBuilder = TypeSpec
        .interfaceBuilder("${it.name.capitalize()}Api")
        .addModifiers(KModifier.PUBLIC)

      addApiPathMethods(it.name, apiInterfaceTypeSpecBuilder, classNameList)

      val typeSpec = apiInterfaceTypeSpecBuilder.build()

      map[it.name] = typeSpec
    }

    return map
  }

  private fun addApiPathMethods(endpoint: String, apiInterfaceTypeSpec: TypeSpec.Builder, classNameList: List<String>) {
    println("endpoint = [${endpoint}], apiInterfaceTypeSpec = [${apiInterfaceTypeSpec}], classNameList = [${classNameList}]")
    println("-----------------------------")
    if (swaggerModel.paths != null && !swaggerModel.paths.isEmpty()) {
      for (path in swaggerModel.paths) {
        for (operation in path.value.operationMap) {

          println(path.key.removePrefix("/"))

          if (path.key.removePrefix("/").startsWith(endpoint, true)) {

            val hasMultipart = operation.value.parameters.any { it.`in`.contains("formData") }
            val customUrl = operation.value.parameters.any { it.`in`.contains("url") }

            val annotationSpec: AnnotationSpec = when {
              operation.key.name.contains(
                "GET") -> {
                val builder = AnnotationSpec.builder(GET::class)

                if (!customUrl) {
                  builder.addMember("\"${path.key.removePrefix("/")}\"")
                }

                builder.build()
              }
              operation.key.name.contains(
                "POST") -> {
                val builder = AnnotationSpec.builder(POST::class)

                if (!customUrl) {
                  builder.addMember("\"${path.key.removePrefix("/")}\"")
                }

                builder.build()
              }
              operation.key.name.contains(
                "PUT") -> {
                val builder = AnnotationSpec.builder(PUT::class)

                if (!customUrl) {
                  builder.addMember("\"${path.key.removePrefix("/")}\"")
                }

                builder.build()
              }
              operation.key.name.contains(
                "PATCH") -> {
                val builder = AnnotationSpec.builder(PATCH::class)

                if (!customUrl) {
                  builder.addMember("\"${path.key.removePrefix("/")}\"")
                }

                builder.build()
              }
              operation.key.name.contains(
                "DELETE") -> {
                val builder = AnnotationSpec.builder(DELETE::class)

                if (!customUrl) {
                  builder.addMember("\"${path.key.removePrefix("/")}\"")
                }

                builder.build()
              }
              operation.key.name.contains(
                "URL") -> AnnotationSpec.builder(GET::class).build()
              else -> AnnotationSpec.builder(GET::class).addMember("\"${path.key.removePrefix("/")}\"").build()
            }

            try {
              val doc = ((listOf(operation.value.summary + "\n") + getMethodParametersDocs(operation)).joinToString("\n")).trim()

              val returnedClass = if (hasMultipart) Single::class.asClassName().parameterizedBy(TypeVariableName.invoke(ResponseBody::class.java.name)) else getReturnedClass(operation, classNameList)
              val methodParameters = getMethodParameters(operation)
              val builder = FunSpec.builder(operation.value.operationId)

              if (hasMultipart) {
                builder.addAnnotation(AnnotationSpec.builder(Multipart::class).build())
              }
              val funSpec = builder
                .addModifiers(KModifier.PUBLIC, KModifier.ABSTRACT)
                .addAnnotation(annotationSpec)
                .addParameters(methodParameters)
                .returns(returnedClass)
                .addKdoc("$doc\n")
                .build()

              apiInterfaceTypeSpec.addFunction(funSpec)
            } catch (exception: Exception) {
              errorTracking.logException(exception)
            }
          }
        }
      }
    }
  }

  private fun parseAnnotationFromAdditionalMethod(apiPath: ApiPath): AnnotationSpec {
    return when {
      apiPath.type.contains("GET", true) -> {
        val builder = AnnotationSpec.builder(GET::class)
        apiPath.path?.let {
          builder.addMember("\"${it.removePrefix("/")}\"")
        }
        builder.build()
      }
      apiPath.type.contains("POST", true) -> {
        val builder = AnnotationSpec.builder(POST::class)
        apiPath.path?.let {
          builder.addMember("\"${it.removePrefix("/")}\"")
        }
        builder.build()
      }
      apiPath.type.contains("PUT", true) -> {
        val builder = AnnotationSpec.builder(PUT::class)
        apiPath.path?.let {
          builder.addMember("\"${it.removePrefix("/")}\"")
        }
        builder.build()
      }
      apiPath.type.contains("PATCH", true) -> {
        val builder = AnnotationSpec.builder(PATCH::class)
        apiPath.path?.let {
          builder.addMember("\"${it.removePrefix("/")}\"")
        }
        builder.build()
      }
      apiPath.type.contains("DELETE", true) -> {
        val builder = AnnotationSpec.builder(DELETE::class)
        apiPath.path?.let {
          builder.addMember("\"${it.removePrefix("/")}\"")
        }
        builder.build()
      }
      apiPath.type.contains("Multipart", true) -> AnnotationSpec.builder(Multipart::class).build()
      else -> {
        val builder = AnnotationSpec.builder(GET::class)
        apiPath.path?.let {
          builder.addMember("\"${it.removePrefix("/")}\"")
        }
        builder.build()
      }
    }
  }

  private fun parseParametersFromAdditionalMethod(apiPath: List<protein.additional.data.Parameter>): Iterable<ParameterSpec> {
    return apiPath.map { parametr ->
      if (parametr.annotation.isBlank()) {
        ParameterSpec.builder(parametr.name, MultipartBody.Part::class.java).build()
      } else {
        when {
          parametr.annotation.contains("body", true) -> {
            ParameterSpec.builder(parametr.name, ClassName.bestGuess(parametr.type).requiredOrNullable(true))
              .addAnnotation(AnnotationSpec.builder(Body::class).build()).build()
          }
          //TODO
//          parametr.annotation.contains("path", true) -> {
//            ParameterSpec.builder(parametr.name, MultipartBody.Part::class.java)
//              .addAnnotation(AnnotationSpec.builder(Path::class).build()).build()
//          }
//          parametr.annotation.contains("query", true) -> {
//            ParameterSpec.builder(parametr.name, MultipartBody.Part::class.java)
//              .addAnnotation(AnnotationSpec.builder(Query::class).addMember("\"${parametr.name}\"").build()).build()
//          }
          parametr.annotation.contains("formData", true) -> {
            ParameterSpec.builder(parametr.name, MultipartBody.Part::class.java)
              .addAnnotation(AnnotationSpec.builder(Part::class).build()).build()
          }
          parametr.annotation.contains("Url", true) -> {
            ParameterSpec.builder(parametr.name, String::class.asClassName()).addAnnotation(AnnotationSpec.builder(Url::class).build()).build()
          }
          else -> {
            ParameterSpec.builder(parametr.name, Any::class.java).build()
          }
        }
      }
    }
  }

  private fun getMethodParametersDocs(operation: MutableMap.MutableEntry<HttpMethod, Operation>): Iterable<String> {
    return operation.value.parameters.filterNot { it.description.isNullOrBlank() }.map { "@param ${it.name} ${it.description}" }
  }

  private fun getTypeName(modelProperty: Map.Entry<String, Property>): TypeName {
    val property = modelProperty.value
    return when {
      property.type == REF_SWAGGER_TYPE ->
        TypeVariableName.invoke((property as RefProperty).simpleRef).requiredOrNullable(property.required)

      property.type == ARRAY_SWAGGER_TYPE -> {
        val arrayProperty = property as ArrayProperty
        getTypedArray(arrayProperty.items).requiredOrNullable(arrayProperty.required)
      }
      else -> getKotlinClassTypeName(property.type, property.format).requiredOrNullable(property.required)
    }
  }

  private fun getMethodParameters(
    operation: MutableMap.MutableEntry<HttpMethod, Operation>
  ): Iterable<ParameterSpec> {
    return operation.value.parameters.mapNotNull { parameter ->
      // Transform parameters in the format foo.bar to fooBar
      val name = parameter.name.split('.').mapIndexed { index, s -> if (index > 0) s.capitalize() else s }.joinToString("")
      when (parameter.`in`) {
        "body" -> {
          ParameterSpec.builder(name, getBodyParameterSpec(parameter))
            .addAnnotation(AnnotationSpec.builder(Body::class).build()).build()
        }
        "path" -> {
          val type = getKotlinClassTypeName((parameter as PathParameter).type, parameter.format).requiredOrNullable(parameter.required)
          ParameterSpec.builder(name, type)
            .addAnnotation(AnnotationSpec.builder(Path::class).addMember("\"${parameter.name}\"").build()).build()
        }
        "query" -> {
          if ((parameter as QueryParameter).type == ARRAY_SWAGGER_TYPE) {
            val type = List::class.asClassName().parameterizedBy(getKotlinClassTypeName(parameter.items.type)).requiredOrNullable(parameter.required)
            ParameterSpec.builder(name, type)
          } else {
            val type = getKotlinClassTypeName(parameter.type, parameter.format).requiredOrNullable(parameter.required)
            ParameterSpec.builder(name, type)
          }.addAnnotation(AnnotationSpec.builder(Query::class).addMember("\"${parameter.name}\"").build()).build()
        }
        "formData" -> {
          ParameterSpec.builder(name, MultipartBody.Part::class.java)
            .addAnnotation(AnnotationSpec.builder(Part::class).build()).build()
        }
        "url" -> {
          ParameterSpec.builder(name, String::class.java)
            .addAnnotation(AnnotationSpec.builder(Url::class).build()).build()
        }
        else -> null
      }
    }
  }

  private fun getBodyParameterSpec(parameter: Parameter): TypeName {
    val bodyParameter = parameter as BodyParameter
    val schema = bodyParameter.schema

    return when (schema) {
      is RefModel -> ClassName.bestGuess(schema.simpleRef.capitalize()).requiredOrNullable(parameter.required)

      is ArrayModel -> getTypedArray(schema.items).requiredOrNullable(parameter.required)

      else -> {
        val bodyParameter1 = parameter.schema as? ModelImpl ?: ModelImpl()

        if (STRING_SWAGGER_TYPE == bodyParameter1.type) {
          String::class.asClassName().requiredOrNullable(parameter.required)
        } else {
          ClassName.bestGuess(parameter.name.capitalize()).requiredOrNullable(parameter.required)
        }
      }
    }
  }

  private fun getTypedArray(items: Property): TypeName {
    val typeProperty = when (items) {
      is LongProperty -> TypeVariableName.invoke(Long::class.simpleName!!)
      is IntegerProperty -> TypeVariableName.invoke(Int::class.simpleName!!)
      is FloatProperty -> TypeVariableName.invoke(Float::class.simpleName!!)
      is DoubleProperty -> TypeVariableName.invoke(Double::class.simpleName!!)
      is RefProperty -> TypeVariableName.invoke(items.simpleRef)
      else -> getKotlinClassTypeName(items.type, items.format)
    }
    return List::class.asClassName().parameterizedBy(typeProperty)
  }

  private fun TypeName.requiredOrNullable(required: Boolean) = if (required) this else asNullable()

  private fun getReturnedClass(
    operation: MutableMap.MutableEntry<HttpMethod, Operation>,
    classNameList: List<String>
  ): TypeName {
    try {
      if (operation.value.responses[OK_RESPONSE]?.schema != null &&
        operation.value.responses[OK_RESPONSE]?.schema is RefProperty) {
        val refProperty = (operation.value.responses[OK_RESPONSE]?.schema as RefProperty)
        var responseClassName = refProperty.simpleRef
        responseClassName = getValidClassName(responseClassName, refProperty)

        if (classNameList.contains(responseClassName)) {
          return Single::class.asClassName().parameterizedBy(TypeVariableName.invoke(responseClassName))
        }
      } else if (operation.value.responses[OK_RESPONSE]?.schema != null &&
        operation.value.responses[OK_RESPONSE]?.schema is ArrayProperty) {
        val refProperty = (operation.value.responses[OK_RESPONSE]?.schema as ArrayProperty)
        var responseClassName = (refProperty.items as RefProperty).simpleRef
        responseClassName = getValidClassName(responseClassName, (refProperty.items as RefProperty))

        if (classNameList.contains(responseClassName)) {
          return Single::class.asClassName().parameterizedBy(
            List::class.asClassName().parameterizedBy(TypeVariableName.invoke(responseClassName))
          )
        }
      }
    } catch (error: ClassCastException) {
      errorTracking.logException(error)
    }

    return Completable::class.asClassName()
  }

  private fun getValidClassName(responseClassName: String, refProperty: RefProperty): String {
    var className = responseClassName
    try {
      TypeSpec.classBuilder(className)
    } catch (error: IllegalArgumentException) {
      if (refProperty.simpleRef != null) {
        className = "Model" + refProperty.simpleRef.capitalize()
      }
    }
    return className
  }

  private fun getKotlinClassTypeName(type: String, format: String? = null): TypeName {
    return when (type) {
      ARRAY_SWAGGER_TYPE -> TypeVariableName.invoke(List::class.simpleName!!)
      STRING_SWAGGER_TYPE -> TypeVariableName.invoke(String::class.simpleName!!)
      NUMBER_SWAGGER_TYPE -> TypeVariableName.invoke(Double::class.simpleName!!)
      INTEGER_SWAGGER_TYPE -> {
        when (format) {
          "int64" -> TypeVariableName.invoke(Long::class.simpleName!!)
          else -> TypeVariableName.invoke(Int::class.simpleName!!)
        }
      }
      else -> TypeVariableName.invoke(type.capitalize())
    }
  }

/*private fun getPropertyInitializer(type: String): String {
    return when (type) {
        ARRAY_SWAGGER_TYPE -> "ArrayList()"
        INTEGER_SWAGGER_TYPE -> "0"
        STRING_SWAGGER_TYPE -> "\"\""
        BOOLEAN_SWAGGER_TYPE -> "false"
        else -> "null"
    }
}*/

  fun getGeneratedApiInterfaceString(): List<String> {
    return StorageUtils.generateString(proteinApiConfiguration.packageName, apiInterfaceTypeSpec.values.toMutableList())
  }

//  fun getGeneratedModelsString(): String {
//    var generated = ""
//    for (typeSpec in responseBodyModelListTypeSpec) {
//      generated += StorageUtils.generateString(proteinApiConfiguration.packageName, typeSpec)
//    }
//    return generated
//  }
//
//  fun getGeneratedEnums(): String {
//    var generated = ""
//    for (typeSpec in enumListTypeSpec) {
//      generated += StorageUtils.generateString(proteinApiConfiguration.packageName, typeSpec)
//    }
//    return generated
//  }
}

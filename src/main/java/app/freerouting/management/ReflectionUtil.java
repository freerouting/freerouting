package app.freerouting.management;

import com.google.gson.annotations.SerializedName;

import java.lang.reflect.Field;

public class ReflectionUtil
{

  public static void setFieldValue(Object obj, String propertyName, Object newValue) throws NoSuchFieldException, IllegalAccessException
  {
    String[] propertyPath = propertyName.split("[.:\\-]");
    Object currentObject = obj;
    Field field = null;

    // Navigate to the nested object
    for (int i = 0; i < propertyPath.length - 1; i++)
    {
      field = getFieldByNameOrSerializedName(currentObject.getClass(), propertyPath[i]);
      field.setAccessible(true);
      currentObject = field.get(currentObject);
    }

    // Set the final field value
    field = getFieldByNameOrSerializedName(currentObject.getClass(), propertyPath[propertyPath.length - 1]);
    field.setAccessible(true);
    Object convertedValue = convertValue(field.getType(), newValue);
    field.set(currentObject, convertedValue);
  }

  private static Field getFieldByNameOrSerializedName(Class<?> clazz, String name) throws NoSuchFieldException
  {
    for (Field field : clazz.getDeclaredFields())
    {
      if (field.getName().equals(name))
      {
        return field;
      }
      SerializedName annotation = field.getAnnotation(SerializedName.class);
      if (annotation != null && annotation.value().equals(name))
      {
        return field;
      }
    }
    throw new NoSuchFieldException("No field found with name or SerializedName: " + name);
  }

  private static Object convertValue(Class<?> targetType, Object value)
  {
    if (targetType.isInstance(value))
    {
      return value;
    }
    if (targetType == int.class || targetType == Integer.class)
    {
      return Integer.parseInt(value.toString());
    }
    if (targetType == long.class || targetType == Long.class)
    {
      return Long.parseLong(value.toString());
    }
    if (targetType == double.class || targetType == Double.class)
    {
      return Double.parseDouble(value.toString());
    }
    if (targetType == boolean.class || targetType == Boolean.class)
    {
      return Boolean.parseBoolean(value.toString());
    }
    // Add more type conversions as needed
    return value;
  }

  /* Copy all non-null fields from one object to another recursively */
  public static void copyFields(Object source, Object target)
  {
    for (Field field : source.getClass().getDeclaredFields())
    {
      try
      {
        field.setAccessible(true);
        Object value = field.get(source);
        if (value != null)
        {
          if (field.getType().isPrimitive() || field.getType() == String.class)
          {
            field.set(target, value);
          }
          else
          {
            Object targetField = field.get(target);
            if (targetField == null)
            {
              targetField = field.getType().newInstance();
              field.set(target, targetField);
            }
            copyFields(value, targetField);
          }
        }
      } catch (IllegalAccessException | InstantiationException e)
      {
        e.printStackTrace();
      }
    }

  }
}
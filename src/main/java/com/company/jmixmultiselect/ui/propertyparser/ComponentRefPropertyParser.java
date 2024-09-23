package com.company.jmixmultiselect.ui.propertyparser;

import io.jmix.flowui.component.UiComponentUtils;
import io.jmix.flowui.xml.layout.ComponentLoader;
import io.jmix.flowui.xml.layout.loader.PropertyParser;
import io.jmix.flowui.xml.layout.loader.PropertyParsingContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(990)
@Component("flowui_ComponentRefPropertyParser")
public class ComponentRefPropertyParser implements PropertyParser {

  public static final String TYPE = "COMPONENT_REF";

  @Override
  public boolean supports(PropertyParsingContext context) {
    return TYPE.equals(context.type())
        && context.context() instanceof ComponentLoader.ComponentContext;
  }

  @Override
  public Object parse(PropertyParsingContext context) {
    ComponentLoader.Context ctx = context.context();
    if (ctx instanceof ComponentLoader.ComponentContext componentContext) {
      return UiComponentUtils.findComponent(componentContext.getView(), context.value())
          .orElseThrow(() -> new IllegalArgumentException(
              "Component with id " + context.value() + " not found"));
    } else {
      throw new IllegalArgumentException(
          "Cannot find component, component loader 'context' must implement "
              + ComponentLoader.ComponentContext.class.getName());
    }
  }
}
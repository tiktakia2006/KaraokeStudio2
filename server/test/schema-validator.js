const typeMatches = (type, value) => {
  switch (type) {
    case 'string':
      return typeof value === 'string';
    case 'integer':
      return Number.isInteger(value);
    case 'number':
      return typeof value === 'number';
    case 'boolean':
      return typeof value === 'boolean';
    case 'null':
      return value === null;
    case 'array':
      return Array.isArray(value);
    case 'object':
      return value !== null && typeof value === 'object' && !Array.isArray(value);
    default:
      return false;
  }
};

export function validate(schema, value, spec, path = '$') {
  if (schema.$ref) {
    const name = schema.$ref.replace('#/components/schemas/', '');
    return validate(spec.components.schemas[name], value, spec, path);
  }
  const errors = [];
  if (schema.oneOf) {
    const matches = schema.oneOf.filter((option) => validate(option, value, spec, path).length === 0);
    if (matches.length !== 1) errors.push(`${path}: очікувався рівно один варіант oneOf, збігів ${matches.length}`);
    return errors;
  }
  if ('const' in schema && value !== schema.const) errors.push(`${path}: очікувалось ${JSON.stringify(schema.const)}`);
  if (schema.enum && !schema.enum.includes(value)) errors.push(`${path}: значення ${JSON.stringify(value)} поза enum`);
  if (schema.type) {
    const types = Array.isArray(schema.type) ? schema.type : [schema.type];
    if (!types.some((type) => typeMatches(type, value))) {
      errors.push(`${path}: очікувався тип ${types.join('|')}, отримано ${JSON.stringify(value)}`);
      return errors;
    }
  }
  if (schema.type === 'object' || (Array.isArray(schema.type) && schema.type.includes('object') && typeMatches('object', value))) {
    for (const key of schema.required ?? []) {
      if (!(key in value)) errors.push(`${path}: відсутнє поле ${key}`);
    }
    for (const [key, item] of Object.entries(value)) {
      if (schema.properties?.[key]) errors.push(...validate(schema.properties[key], item, spec, `${path}.${key}`));
      else if (schema.additionalProperties === false) errors.push(`${path}: непередбачене поле ${key}`);
    }
  }
  if (schema.type === 'array' && schema.items) {
    value.forEach((item, index) => errors.push(...validate(schema.items, item, spec, `${path}[${index}]`)));
  }
  return errors;
}

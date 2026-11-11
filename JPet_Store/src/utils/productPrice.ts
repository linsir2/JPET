/**
 * 与后端静态资源中的历史 calculateProductPrice 逻辑对齐（去掉调试输出）
 */
function normalizeText(value) {
  return String(value ?? '').trim().toLowerCase()
}

function getVariantPrices(product) {
  return Array.isArray(product?.variantPrices) ? product.variantPrices : []
}

function findVariant(product, specification, size, selectedAttributes = undefined) {
  const variants = getVariantPrices(product)
  if (!variants.length) return null

  const normalizedSpec = normalizeText(specification)
  const normalizedSize = normalizeText(size)
  const normalizedAttributes = Object.fromEntries(
    Object.entries(selectedAttributes || {}).map(([key, value]) => [normalizeText(key), normalizeText(value)]),
  )

  if (normalizedSpec) {
    const exact = variants.find((variant) => normalizeText(variant?.combination) === normalizedSpec)
    if (exact) return exact
  }

  const matchedByAttributes = variants.find((variant) => {
    const values = Object.fromEntries(
      Object.entries(variant?.values || {}).map(([key, value]) => [normalizeText(key), normalizeText(value)]),
    )

    if (Object.keys(normalizedAttributes).length > 0) {
      return Object.entries(normalizedAttributes).every(([key, value]) => !value || values[key] === value)
    }

    if (normalizedSpec) {
      return Object.values(values).some((value) => value === normalizedSpec)
        || normalizeText(variant?.combination).includes(normalizedSpec)
    }

    if (normalizedSize) {
      return Object.entries(values).some(
        ([key, value]) => (key.includes('size') || key.includes('尺寸')) && value === normalizedSize,
      )
    }

    return false
  })

  return matchedByAttributes || variants[0]
}

export function calculateProductPrice(product, specification, size) {
  if (!product) return 0

  const variant = findVariant(product, specification, size)
  if (variant?.price != null) {
    return parseFloat(variant.price) || 0
  }

  let specPrices = product.specificationPrices
  if (!specPrices && product.attributes) {
    specPrices = product.attributes.specificationPrices
  }

  let sizePrices = product.sizePrices
  if (!sizePrices && product.attributes) {
    sizePrices = product.attributes.sizePrices
  }

  if (specification && specPrices && typeof specPrices === 'object' && specPrices !== null) {
    let price = specPrices[specification]
    if (price == null && Object.keys(specPrices).length > 0) {
      const matchingKey = Object.keys(specPrices).find(
        (key) =>
          key.toLowerCase() === specification.toLowerCase() || key.trim() === specification.trim(),
      )
      if (matchingKey) price = specPrices[matchingKey]
    }
    if (price != null && price !== undefined) {
      return parseFloat(price)
    }
  }

  if (size && sizePrices && typeof sizePrices === 'object' && sizePrices !== null) {
    let price = sizePrices[size]
    if (price == null && Object.keys(sizePrices).length > 0) {
      const matchingKey = Object.keys(sizePrices).find(
        (key) => key.toLowerCase() === size.toLowerCase() || key.trim() === size.trim(),
      )
      if (matchingKey) price = sizePrices[matchingKey]
    }
    if (price != null && price !== undefined) {
      return parseFloat(price)
    }
  }

  return parseFloat(product.price || 0)
}

export function calculateCartItemPrice(item) {
  const product = item.product || {}
  const selectedAttributes = item.selectedAttributes || {}

  let displayPrice = product.price != null ? product.price : (item.price || 0)

  const variant = findVariant(
    product,
    selectedAttributes.specification,
    selectedAttributes.size,
    selectedAttributes,
  )
  if (variant?.price != null) {
    return parseFloat(variant.price) || 0
  }

  let specPrices = product.specificationPrices || (product.attributes && product.attributes.specificationPrices)
  let sizePrices = product.sizePrices || (product.attributes && product.attributes.sizePrices)

  if (selectedAttributes.specification && specPrices && specPrices[selectedAttributes.specification]) {
    displayPrice = parseFloat(specPrices[selectedAttributes.specification])
  } else if (selectedAttributes.size && sizePrices && sizePrices[selectedAttributes.size]) {
    displayPrice = parseFloat(sizePrices[selectedAttributes.size])
  }

  return parseFloat(displayPrice) || 0
}

export function calculateTotal(cartItems) {
  return cartItems.reduce((sum, item) => {
    const price = calculateCartItemPrice(item)
    const quantity = Number(item.quantity) || 0
    return sum + price * quantity
  }, 0)
}

export function calculateTotalQuantity(cartItems) {
  return cartItems.reduce((sum, item) => sum + (Number(item.quantity) || 0), 0)
}

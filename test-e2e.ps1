echo "Building the application..."
.\mvnw.cmd clean package -DskipTests | Out-Null

echo "Starting the application..."
$process = Start-Process -FilePath "java" -ArgumentList "-jar target/ecommerce-0.0.1-SNAPSHOT.jar" -PassThru -WindowStyle Hidden

echo "Waiting for application to start (15 seconds)..."
Start-Sleep -Seconds 15

try {
    echo "======================================"
    echo "1. Creating a new Cart"
    $cart = Invoke-RestMethod -Uri "http://localhost:8080/api/carts" -Method Post
    $cartId = $cart.cartId
    echo "-> Cart created successfully with ID: $cartId"

    echo "`n======================================"
    echo "2. Adding Items to Cart (Product 1: Wireless Headphones, Qty: 2)"
    $body = @{ productId = 1; quantity = 2 } | ConvertTo-Json
    $cartUpdated = Invoke-RestMethod -Uri "http://localhost:8080/api/carts/$cartId/items" -Method Post -Body $body -ContentType "application/json"
    echo "-> Items added! Current Cart Total: $($cartUpdated.totalAmount)"

    echo "`n======================================"
    echo "3. Checking Out (with Idempotency Key 'key-999')"
    $checkoutBody = @{ cartId = $cartId } | ConvertTo-Json
    $order = Invoke-RestMethod -Uri "http://localhost:8080/api/checkout" -Method Post -Headers @{ "X-Idempotency-Key" = "key-999" } -Body $checkoutBody -ContentType "application/json"
    echo "-> Checkout successful! Order ID: $($order.orderId) | Net Total: $($order.netTotal)"

    echo "`n======================================"
    echo "4. Testing Idempotency (Retrying exact same checkout)"
    $orderRetry = Invoke-RestMethod -Uri "http://localhost:8080/api/checkout" -Method Post -Headers @{ "X-Idempotency-Key" = "key-999" } -Body $checkoutBody -ContentType "application/json"
    echo "-> Retry successful! Returned cached Order ID: $($orderRetry.orderId) (Matches original: $($order.orderId -eq $orderRetry.orderId))"

    echo "`n======================================"
    echo "5. Generating Admin Report"
    $report = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/reports/summary" -Method Get
    echo "-> Admin Report Summary:"
    $report | ConvertTo-Json -Depth 3
    
    echo "`n======================================"
    echo "✅ ALL LIVE ENDPOINT TESTS PASSED SUCCESSFULLY!"

} catch {
    echo "❌ An error occurred during testing:"
    echo $_.Exception.Message
    if ($_.ErrorDetails) {
        echo $_.ErrorDetails.Message
    }
} finally {
    echo "`nStopping the application..."
    Stop-Process -Id $process.Id -Force
}

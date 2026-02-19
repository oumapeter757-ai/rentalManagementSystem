 #!/bin/bash

# Quick Fix Script - Temporarily disable new features to allow compilation

echo "Temporarily moving new files that cause compilation errors..."

# Create a backup directory
mkdir -p /home/peter/rentalManagementSystem/TEMPORARILY_DISABLED

# Move problematic new files
mv /home/peter/rentalManagementSystem/src/main/java/com/peterscode/rentalmanagementsystem/model/sms /home/peter/rentalManagementSystem/TEMPORARILY_DISABLED/
mv /home/peter/rentalManagementSystem/src/main/java/com/peterscode/rentalmanagementsystem/model/payment/MonthlyPaymentHistory.java /home/peter/rentalManagementSystem/TEMPORARILY_DISABLED/
mv /home/peter/rentalManagementSystem/src/main/java/com/peterscode/rentalmanagementsystem/repository/MonthlyPaymentHistoryRepository.java /home/peter/rentalManagementSystem/TEMPORARILY_DISABLED/
mv /home/peter/rentalManagementSystem/src/main/java/com/peterscode/rentalmanagementsystem/repository/SmsReminderRepository.java /home/peter/rentalManagementSystem/TEMPORARILY_DISABLED/
mv /home/peter/rentalManagementSystem/src/main/java/com/peterscode/rentalmanagementsystem/service/payment/MonthlyPaymentHistoryService.java /home/peter/rentalManagementSystem/TEMPORARILY_DISABLED/
mv /home/peter/rentalManagementSystem/src/main/java/com/peterscode/rentalmanagementsystem/scheduler /home/peter/rentalManagementSystem/TEMPORARILY_DISABLED/
mv /home/peter/rentalManagementSystem/src/main/java/com/peterscode/rentalmanagementsystem/dto/response/MonthlyPaymentHistoryResponse.java /home/peter/rentalManagementSystem/TEMPORARILY_DISABLED/

echo "Files moved. Now trying to compile..."
cd /home/peter/rentalManagementSystem
./mvnw clean compile -DskipTests

echo ""
echo "If compilation succeeds, you can:"
echo "1. Fix the Lombok issue in your IDE (IntelliJ IDEA -> Invalidate Caches)"
echo "2. Restore the files from TEMPORARILY_DISABLED directory"
echo "3. Or implement the features in a properly configured IDE"


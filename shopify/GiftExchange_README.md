# Holiday Gift Exchange Program

## 概述

这个程序实现了假日礼物交换（Secret Santa）功能。它从CSV文件读取参与者信息，随机匹配每个人，确保：
- 没有人给自己买礼物
- 每个人都要给一个人买礼物
- 每个人都要收到一个人的礼物

## 文件结构

- `Participant.java` - 参与者类，存储姓名和邮箱
- `GiftExchange.java` - 主要的礼物交换逻辑
- `group1.csv` - 示例CSV文件1
- `group2.csv` - 示例CSV文件2

## CSV文件格式

CSV文件应该包含参与者的姓名和邮箱，格式如下：

```
name,email
Alice,alice@example.com
Bob,bob@example.com
Charlie,charlie@example.com
```

每行一个参与者，格式为：`姓名,邮箱`

## 编译和运行

### 编译

```bash
javac src/main/java/Participant.java src/main/java/GiftExchange.java
```

### 运行

```bash
java -cp src/main/java GiftExchange group1.csv
```

或者使用其他CSV文件：

```bash
java -cp src/main/java GiftExchange group2.csv
```

## 输出示例

程序会输出类似以下的结果：

```
Reading participants from: group1.csv
Found 5 participants

Generating random matches...

=== Holiday Gift Exchange Matches ===

Alice -> Bob
  Email: alice@example.com
  You are buying a gift for: Bob (bob@example.com)

Bob -> Charlie
  Email: bob@example.com
  You are buying a gift for: Charlie (charlie@example.com)

Charlie -> Diana
  Email: charlie@example.com
  You are buying a gift for: Diana (diana@example.com)

Diana -> Eve
  Email: diana@example.com
  You are buying a gift for: Eve (eve@example.com)

Eve -> Alice
  Email: eve@example.com
  You are buying a gift for: Alice (alice@example.com)

Total participants: 5
```

## 功能特性

1. **读取CSV文件** - 从CSV文件读取参与者信息
2. **随机匹配** - 使用随机算法生成匹配
3. **防止自匹配** - 确保没有人匹配到自己
4. **完整匹配** - 确保每个人都是给予者和接收者
5. **格式化输出** - 清晰显示匹配结果

## 注意事项

- 至少需要2个参与者才能进行礼物交换
- 程序不会实际发送邮件（根据要求）
- 每次运行会生成不同的随机匹配
- CSV文件应该使用UTF-8编码

## 扩展功能（未来）

虽然当前实现只打印匹配结果，但程序设计允许未来添加：
- 邮件发送功能（需要邮件服务器配置）
- 排除某些匹配（例如家庭成员之间不匹配）
- 历史记录和统计

## 测试

运行测试：

```bash
javac -cp "lib/*:." src/main/java/*.java src/test/java/*.java
java -cp "lib/*:." org.junit.platform.console.ConsoleLauncher --class-path . --scan-class-path
```

#!/bin/bash
cd ~/aarishai || exit

# पुराना कचरा साफ करो
> /tmp/exact_code_dump.txt

# सिर्फ रूट फोल्डर की काम की फाइल्स (.py, .sh, gradle, yaml, properties) ढूंढेंगे 
find . -maxdepth 1 -type f \( -name "*.py" -o -name "*.sh" -o -name "build.gradle" -o -name "*.yaml" -o -name "*.properties" \) | sort | while read -r f; do
    
    # "./" को हटा कर सिर्फ फाइल का नाम निकालना (जैसे script.py)
    clean_name="${f#./}"
    
    # एकदम वही फॉर्मेट जो तुमने फाइल में भेजा है
    echo "--- FILE: $clean_name ---" >> /tmp/exact_code_dump.txt
    cat "$f" >> /tmp/exact_code_dump.txt
    echo -e "\n\n" >> /tmp/exact_code_dump.txt
done

# पूरा डेटा एक साथ क्लिपबोर्ड में कॉपी
cat /tmp/exact_code_dump.txt | termux-clipboard-set
echo "✅ सारी Python, Bash और Config फाइल्स exact फॉर्मेट में कॉपी हो गईं!"

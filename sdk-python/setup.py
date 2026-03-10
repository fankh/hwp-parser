from setuptools import setup, find_packages

setup(
    name="hwp-parser",
    version="1.0.0",
    description="HWP 5.0 바이너리 문서 파서",
    packages=find_packages(),
    python_requires=">=3.8",
    install_requires=[
        "olefile>=0.46",
    ],
)

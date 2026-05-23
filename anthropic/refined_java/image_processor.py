from PIL import Image, ImageFilter, ImageOps
import json
import os
from pathlib import Path


# {
#   "transformations": [
#     { "type": "grayscale" },
#     { "type": "scale", "factor": 0.5 },
#     { "type": "rotate", "angle": 90 }
#   ]
# }

def load_transformations(json_path: str) -> list:
    """Load transformation specifications from a JSON file."""
    with open(json_path, 'r') as f:
        data = json.load(f)
    return data.get('transformations', [])

def apply_transformation(image: Image.Image, transform: dict) -> Image.Image:
    """Apply a single transformation to an image."""
    transform_type = transform['type']

    if transform_type == 'grayscale':
        # Convert to grayscale. We convert back to RGB because 
        # some later steps might expect 3 color channels.
        return ImageOps.grayscale(image).convert('RGB')

    elif transform_type == 'flip_horizontal':
        return ImageOps.mirror(image)

    elif transform_type == 'flip_vertical':
        return ImageOps.flip(image)

    elif transform_type == 'scale':
        factor = transform['factor']
        new_size = (int(image.width * factor), int(image.height * factor))
        return image.resize(new_size, Image.Resampling.LANCZOS)

    elif transform_type == 'blur':
        radius = transform['radius']
        return image.filter(ImageFilter.GaussianBlur(radius=radius))

    elif transform_type == 'rotate':
        angle = transform['angle']
        return image.rotate(angle, expand=True)

    else:
        raise ValueError(f"Unknown transformation type: {transform_type}")

def apply_all_transformations(image: Image.Image, transformations: list) -> Image.Image:
    """Apply a sequence of transformations to an image."""
    result = image.copy()
    for transform in transformations:
        result = apply_transformation(result, transform)
    return result

def process_images(
    image_dir: str,
    transformation_dir: str,
    output_dir: str,
    get_output_path
) -> None:
    """Process all images with all transformation configurations."""

    # Get all image and transformation files.
    # Note: In a real interview, helper functions might give you these lists.
    image_files = [f for f in Path(image_dir).iterdir()
                   if f.suffix.lower() in ('.png', '.jpg', '.jpeg')]
    transform_files = list(Path(transformation_dir).glob('*.json'))

    for transform_file in transform_files:
        transformations = load_transformations(str(transform_file))

        for image_file in image_files:
            # Load image
            image = Image.open(str(image_file))

            # Apply transformations
            result = apply_all_transformations(image, transformations)

            # Save to output directory
            output_path = get_output_path(str(image_file), str(transform_file))
            result.save(output_path)

            # Close images to free memory
            image.close()
            result.close()


# Fast version
from concurrent.futures import ProcessPoolExecutor
from PIL import Image, ImageFilter, ImageOps
import json
from pathlib import Path
import os

# Note: This function must be at the module level (not inside another function)
# so Python can send it to other processes (pickling).
def apply_transformation(image: Image.Image, transform: dict) -> Image.Image:
    """Apply a single transformation to an image."""
    transform_type = transform['type']

    if transform_type == 'grayscale':
        return ImageOps.grayscale(image).convert('RGB')
    elif transform_type == 'flip_horizontal':
        return ImageOps.mirror(image)
    elif transform_type == 'flip_vertical':
        return ImageOps.flip(image)
    elif transform_type == 'scale':
        factor = transform['factor']
        new_size = (int(image.width * factor), int(image.height * factor))
        return image.resize(new_size, Image.Resampling.LANCZOS)
    elif transform_type == 'blur':
        radius = transform['radius']
        return image.filter(ImageFilter.GaussianBlur(radius=radius))
    elif transform_type == 'rotate':
        angle = transform['angle']
        return image.rotate(angle, expand=True)
    else:
        raise ValueError(f"Unknown transformation type: {transform_type}")

def process_single_image(args: tuple) -> str:
    """Process a single image with a transformation configuration.

    This function runs in a separate process.
    """
    image_path, transform_path, output_path = args

    # Load transformation config
    with open(transform_path, 'r') as f:
        data = json.load(f)
    transformations = data.get('transformations', [])

    # Load and process image
    image = Image.open(image_path)
    result = image.copy()

    for transform in transformations:
        result = apply_transformation(result, transform)

    # Ensure output directory exists and save
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    result.save(output_path)

    image.close()
    result.close()

    return output_path

def process_images_parallel(
    image_dir: str,
    transformation_dir: str,
    output_dir: str,
    get_output_path,
    max_workers: int = None
) -> None:
    """Process all images in parallel using multiple processes."""

    # Collect all work items
    image_files = [f for f in Path(image_dir).iterdir()
                   if f.suffix.lower() in ('.png', '.jpg', '.jpeg')]
    transform_files = list(Path(transformation_dir).glob('*.json'))

    work_items = []
    for transform_file in transform_files:
        for image_file in image_files:
            output_path = get_output_path(str(image_file), str(transform_file))
            work_items.append((str(image_file), str(transform_file), output_path))

    # Process in parallel using multiple CPU cores
    with ProcessPoolExecutor(max_workers=max_workers) as executor:
        results = list(executor.map(process_single_image, work_items))

    print(f"Processed {len(results)} images")